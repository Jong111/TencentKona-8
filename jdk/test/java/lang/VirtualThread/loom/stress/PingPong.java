/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @run main/othervm PingPong SQ 1000000
 * @run main/othervm PingPong LTQ 1000000
 */

import java.time.Duration;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PingPong {

    public static void main(String[] args) throws Exception {
        Exchanger<String> queue;
        int iterations;
        if (args.length == 0) {
            queue = new LTQExchanger<>();
            iterations = 10_000_000;
        } else {
            if (args[0].equals("SQ")) {
                queue = new SQExchanger<>();
            } else {
                queue = new LTQExchanger<>();
            }
            iterations = Integer.parseInt(args[1]);
        }

        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        Thread t1 = Thread.startVirtualThread(() -> {
            try {
                while (count1.incrementAndGet() < iterations) {
                    queue.transfer("hello");
                    String reply = queue.take();
                    if (!"ack".equals(reply)) {
                        throw new RuntimeException("reply=" + reply);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = Thread.startVirtualThread(() -> {
            try {
                while (count2.incrementAndGet() < iterations) {
                    String message = queue.take();
                    if (!"hello".equals(message)) {
                        throw new RuntimeException("message=" + message);
                    }
                    queue.transfer("ack");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.join();
        t2.join();
    }

    interface Exchanger<E> {
        void transfer (E value) throws InterruptedException;
        E take() throws InterruptedException;
    }

    static class SQExchanger<E> implements Exchanger<E> {
        private final SynchronousQueue<E> queue = new SynchronousQueue<>();
        @Override
        public void transfer(E value) throws InterruptedException {
            queue.put(value);
        }
        @Override
        public E take() throws InterruptedException {
            return queue.take();
        }
    }

    static class LTQExchanger<E> implements Exchanger<E> {
        private final LinkedTransferQueue<E> queue = new LinkedTransferQueue<>();
        @Override
        public void transfer(E value) throws InterruptedException {
            queue.transfer(value);
        }
        @Override
        public E take() throws InterruptedException {
            return queue.take();
        }
    }
}
