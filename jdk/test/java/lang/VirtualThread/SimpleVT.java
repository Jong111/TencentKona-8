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
 * @run testng SimpleVT
 * @run testng/othervm -Djdk.internal.VirtualThread=off SimpleVT
 * @summary Basic test for virtual thread, test create/run/yield/resume/stop
 */
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class SimpleVT {
    static long finished_vt_count = 0;

    @Test
    public static void foo() throws Exception {
        finished_vt_count = 0;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("before yield");
                Thread.yield();
                System.out.println("resume yield");
                finished_vt_count++;
            }
        };
        Thread vt = Thread.ofVirtual().scheduler(executor).name("foo_thread").unstarted(target);
        vt.start();
        vt.join();
        //Thread.sleep(1000);
        executor.shutdown();
        assertEquals(finished_vt_count, 1);
    }

    @Test
    public static void FixedThreadPoolSimple() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger ai = new AtomicInteger(0);

        Runnable target = new Runnable() {
            public void run() {
                System.out.println("before yield " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
                Thread.yield();
                System.out.println("resume yield " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
                ai.incrementAndGet();
            }
        };

        Thread[] vts = new Thread[40];
        ThreadFactory f = Thread.ofVirtual().scheduler(executor).name("FixedThreadPoolSimple_", 0).factory();
        for (int i = 0; i < 40; i++) {
            vts[i] = f.newThread(target);
        }
        for (int i = 0; i < 40; i++) {
            vts[i].start();
        }

        for (int i = 0; i < 40; i++) {
            vts[i].join();
        }
        executor.shutdown();
        assertEquals(ai.get(), 40);
    }

    @Test
    public static void singleThreadParkTest() throws Exception {
        final Thread kernel = Thread.currentThread();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Runnable target = new Runnable() {
            public void run() {
                System.out.println("before park " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
                assertEquals(Thread.currentThread().getName(), "park_thread");
                assertNotEquals(Thread.currentCarrierThread(), kernel);
                LockSupport.park();
                System.out.println("after park " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
                assertEquals(Thread.currentThread().getName(), "park_thread");
                assertNotEquals(Thread.currentCarrierThread(), kernel);
            }
        };
        Thread vt = Thread.ofVirtual().scheduler(executor).name("park_thread").unstarted(target);
        vt.start();

        System.out.println("after start " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
        assertEquals(Thread.currentThread(), kernel);
        assertEquals(Thread.currentCarrierThread(), kernel);

        Thread.sleep(500);

        System.out.println("before unpark " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
        assertEquals(Thread.currentThread(), kernel);
        assertEquals(Thread.currentCarrierThread(), kernel);

        LockSupport.unpark(vt);

        System.out.println("after unpark " + Thread.currentThread().getName() + " " + Thread.currentCarrierThread().getName());
        assertEquals(Thread.currentThread(), kernel);
        assertEquals(Thread.currentCarrierThread(), kernel);

        vt.join();
        System.out.println("after join");
        //Thread.sleep(1000);
        executor.shutdown();
    }

    /*
     * Park and unpark in multiple virtual threads
     */
    @Test
    public static void multipleThreadParkTest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Thread[] vts = new Thread[10];
        Runnable target = new Runnable() {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " before park");
                int myIndex = -1;
                for (int i = 0; i < 10; i++) {
                    if (vts[i] == Thread.currentThread()) {
                        myIndex = i;
                        break;
                    }
                }

                assertEquals(Thread.currentThread().getName(), "vt" + myIndex);
                // myIndex from 0 to 9
                if (myIndex > 0) {
                    LockSupport.unpark(vts[myIndex - 1]);
                }
                LockSupport.park();
                System.out.println(Thread.currentThread().getName() + " after park");
                assertEquals(Thread.currentThread().getName(), "vt" + myIndex);
            }
        };
        ThreadFactory f = Thread.ofVirtual().scheduler(executor).name("vt", 0).factory();
        for (int i = 0; i < 10; i++) {
            vts[i] = f.newThread(target);
        }
        for (int i = 0; i < 10; i++) {
            vts[i].start();
        }
        LockSupport.unpark(vts[9]);
        for (int i = 0; i < 10; i++) {
            vts[i].join();
        }
        executor.shutdown();
    }

    @Test
    public static void multipleThreadPoolParkTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Thread[] vts = new Thread[100];
        Runnable target = new Runnable() {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " before park");
                int myIndex = -1;
                for (int i = 0; i < 100; i++) {
                    if (vts[i] == Thread.currentThread()) {
                        myIndex = i;
                        break;
                    }
                }

                assertEquals(Thread.currentThread().getName(), "vt" + myIndex);
                // myIndex from 0 to 99
                if (myIndex > 0) {
                    LockSupport.unpark(vts[myIndex - 1]);
                }
                if (myIndex != 99) {
                    LockSupport.park();
                }
                System.out.println(Thread.currentThread().getName() + " after park");
                assertEquals(Thread.currentThread().getName(), "vt" + myIndex);
            }
        };
        ThreadFactory f = Thread.ofVirtual().scheduler(executor).name("vt", 0).factory();
        for (int i = 0; i < 100; i++) {
            vts[i] = f.newThread(target);
        }
        for (int i = 0; i < 100; i++) {
            vts[i].start();
        }
        for (int i = 0; i < 100; i++) {
            vts[i].join();
        }
        executor.shutdown();
    }

}
