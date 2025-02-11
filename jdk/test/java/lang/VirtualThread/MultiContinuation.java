/*
 * Copyright (C) 2020, 2023, THL A29 Limited, a Tencent company. All rights reserved.
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
 */

/*
 * @test
 * @run testng MultiContinuation
 * @summary Basic test for continuation, test create/run/yield/resume/stop
 */
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class MultiContinuation {
    static long count = 0;
    static ContinuationScope scope = new ContinuationScope("test");

    @Test
    public static void foo() throws Exception {
        final Thread kernel = Thread.currentThread();
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("Continuation first run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread(), kernel);
                Continuation.yield(scope);
                System.out.println("Continuation second run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread(), kernel);
            }
        };
        Continuation[] conts = new Continuation[10];
        for (int i = 0; i < 10; i++) {
            conts[i] = new Continuation(scope, target);;
        }
        for (int i = 0; i < 10; i++) {
            conts[i].run();
        }
        for (int i = 0; i < 10; i++) {
            conts[i].run();
        }
    }

    @Test
    public static void bar() throws Exception {
        final Thread kernel = Thread.currentThread();
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("Continuation first run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread(), kernel);
                Continuation.yield(scope);
                System.out.println("Continuation second run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "bar-thread-0");
                Continuation.yield(scope);
                System.out.println("Continuation third run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread(), kernel);
            }
        };
        Continuation[] conts = new Continuation[10];
        for (int i = 0; i < 10; i++) {
            conts[i] = new Continuation(scope, target);;
        }
        for (int i = 0; i < 10; i++) {
            conts[i].run();
        }
        Thread thread = new Thread("bar-thread-0"){
            public void run() {
                for (int i = 0; i < 10; i++) {
                    conts[i].run();
                }
            }
        };
        thread.start();
        thread.join();
        for (int i = 0; i < 10; i++) {
            conts[i].run();
        }
        //cont.run();
    }

    // create in other thread and run in main
    @Test
    public static void qux() throws Exception {
        final Thread kernel = Thread.currentThread();
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("Continuation first run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "qux-thread-0");
                Continuation.yield(scope);
                System.out.println("Continuation second run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread(), kernel);
                Continuation.yield(scope);
                System.out.println("Continuation third run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "qux-thread-0");
            }
        };
        Continuation[] conts = new Continuation[10];
        for (int i = 0; i < 10; i++) {
            conts[i] = new Continuation(scope, target);;
        }
        CountDownLatch waitSignal1 = new CountDownLatch(1);
        CountDownLatch waitSignal2 = new CountDownLatch(1);
        Thread thread = new Thread("qux-thread-0"){
            public void run() {
                for (int i = 0; i < 10; i++) {
                    conts[i].run();
                }
                waitSignal1.countDown();
                try {
                    // wait main thread finish second round
                    waitSignal2.await();
                } catch (Exception e) {
                }
                for (int i = 0; i < 10; i++) {
                    conts[i].run();
                }
            }
        };
        thread.start();
        // wait thread finish first round run
        waitSignal1.await();
        for (int i = 0; i < 10; i++) {
            conts[i].run();
        }
        waitSignal2.countDown();
        thread.join();
    }

    // racing processing multiple continuations in threads
    @Test
    public static void baz() throws Exception {
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("Continuation first run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "baz-thread-0");
                Continuation.yield(scope);
                System.out.println("Continuation second run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "baz-thread-0");
                Continuation.yield(scope);
                System.out.println("Continuation third run in " + Thread.currentThread().getName());
                assertEquals(Thread.currentThread().getName(), "baz-thread-0");
            }
        };

        LinkedBlockingDeque<Continuation> queue = new LinkedBlockingDeque();
        for (int i = 0; i < 10; i++) {
            queue.offer(new Continuation(scope, target));
        }
        AtomicInteger ai = new AtomicInteger(0);

        Runnable r = new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Continuation c = queue.take();
                        c.run();
                        if (c.isDone() == false) {
                            queue.offer(c);
                        } else {
                            System.out.println("done");
                            int res = ai.incrementAndGet();
                            if(res == 10) {
                                return;
                            }
                        }
                    }
                } catch(InterruptedException e) {
                }
            }
        };

        Thread thread = new Thread(r, "baz-thread-0");
        thread.start();
        thread.join();
    }

    @Test
    public static void quux() throws Exception {
        final Thread kernel = Thread.currentThread();
        CountDownLatch allStartedSignal = new CountDownLatch(1);
        Runnable target = new Runnable() {
            public void run() {
                //System.out.println("Continuation first run in " + Thread.currentThread().getName());
                assertNotEquals(Thread.currentThread(), kernel);
                Continuation.yield(scope);
                //System.out.println("Continuation second run in " + Thread.currentThread().getName());
                assertNotEquals(Thread.currentThread(), kernel);
                Continuation.yield(scope);
                //System.out.println("Continuation third run in " + Thread.currentThread().getName());
                assertNotEquals(Thread.currentThread(), kernel);
            }
        };
        LinkedBlockingDeque<Continuation> queue = new LinkedBlockingDeque();
        for (int i = 0; i < 1000; i++) {
            queue.offer(new Continuation(scope, target));
        }
        AtomicInteger ai = new AtomicInteger(0);

        Thread[] threads = new Thread[50];
        Runnable r = new Runnable() {
            public void run() {
                try {
                    allStartedSignal.await();
                    while (true) {
                        Continuation c = queue.take();
                        c.run();
                        if (c.isDone() == false) {
                            queue.offer(c);
                        } else {
                            int res = ai.incrementAndGet();
                            if (res < 1000)
                                continue;

                            for (int i = 0; i < 50; i++) {
                                if (threads[i] == Thread.currentThread()) {
                                    continue;
                                }
                                threads[i].interrupt();
                            }
                            return;
                        }
                    }
                } catch(InterruptedException e) {
                }
            }
        };

        for (int i = 0; i < 50; i++) {
            threads[i] = new Thread(r);
        }

        for (int i = 0; i < 50; i++) {
            threads[i].start();
        }
        allStartedSignal.countDown();
        for (int i = 0; i < 50; i++) {
            threads[i].join();
        }

        assertEquals(ai.get(), 1000);
    }

}
