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
 * @run main/othervm YieldALot
 * @summary Stress test Thread.yield
 */

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class YieldALot {

    public static void main(String[] args) throws Exception {
        int iterations = 1_000_000;
        if (args.length > 0) {
            iterations = Integer.parseInt(args[0]);
        }
        final int ITERATIONS = iterations;

        AtomicInteger count = new AtomicInteger();

        Thread thread = Thread.startVirtualThread(() -> {
            while (count.incrementAndGet() < ITERATIONS) {
                Thread.yield();
            }
        });

        thread.join();

        int countValue = count.get();
        if (countValue != ITERATIONS) {
            throw new RuntimeException("count = " + countValue);
        }
    }
}
