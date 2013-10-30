/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.java.libuv.Constants;
import net.java.libuv.FileEventCallback;
import net.java.libuv.Files;
import net.java.libuv.handles.FileEventWatchHandle;
import net.java.libuv.handles.LoopHandle;

public class FileEventWatchHandleTest extends TestBase {

    private String testName;

    @BeforeMethod
    protected void startSession(final Method method) throws Exception {
        testName = (TMPDIR.endsWith(File.separator) ? TMPDIR : TMPDIR + File.separator) + method.getName();
    }

    @Test
    public void testFileChangeEvent() throws Exception {
        final AtomicBoolean gotCallback = new AtomicBoolean(false);
        final AtomicBoolean gotClose = new AtomicBoolean(false);
        final AtomicInteger times = new AtomicInteger(0);

        final LoopHandle loop = new LoopHandle();
        final Files handle = new Files(loop);
        final FileEventWatchHandle eventHandle = new FileEventWatchHandle(loop);

        eventHandle.setCloseCallback(new FileEventCallback() {
            @Override
            public void call(final int status, final String event, final String filename) throws Exception {
                System.out.println("file event closed");
                handle.unlink(testName);
                gotClose.set(true);
            }
        });

        eventHandle.setFileEventCallback(new FileEventCallback() {
            @Override
            public void call(final int status, final String event, final String filename) throws Exception {
                Assert.assertEquals(status, 0);
                Assert.assertEquals(event, "change");
                Assert.assertTrue(testName.endsWith(filename));
                gotCallback.set(true);
                System.out.println("file event");
                times.incrementAndGet();
                eventHandle.close();
            }
        });

        final int fd = handle.open(testName, Constants.O_WRONLY | Constants.O_CREAT, Constants.S_IRWXU);
        eventHandle.start(testName, true);
        handle.ftruncate(fd, 1000);

        final long start = System.currentTimeMillis();
        while (!gotCallback.get() || !gotClose.get()) {
            if (System.currentTimeMillis() - start > TIMEOUT) {
                Assert.fail("timeout waiting for file event");
            }
            loop.runNoWait();
        }

        Assert.assertTrue(gotCallback.get());
        Assert.assertTrue(gotClose.get());
        Assert.assertEquals(times.get(), 1);
    }

    @Test
    public void testFileRenameEvent() throws Exception {
        final String newName = testName + "_new";
        final AtomicBoolean gotCallback = new AtomicBoolean(false);
        final AtomicBoolean gotClose = new AtomicBoolean(false);
        final AtomicInteger times = new AtomicInteger(0);

        final LoopHandle loop = new LoopHandle();
        final Files handle = new Files(loop);
        final FileEventWatchHandle eventHandle = new FileEventWatchHandle(loop);

        eventHandle.setCloseCallback(new FileEventCallback() {
            @Override
            public void call(final int status, final String event, final String filename) throws Exception {
                System.out.println("file event closed");
                handle.unlink(newName);
                gotClose.set(true);
            }
        });

        eventHandle.setFileEventCallback(new FileEventCallback() {
            @Override
            public void call(final int status, final String event, final String filename) throws Exception {
                Assert.assertEquals(status, 0);
                Assert.assertEquals(event, "rename");
                Assert.assertTrue(testName.endsWith(filename));
                gotCallback.set(true);
                System.out.println("file event");
                times.incrementAndGet();
                eventHandle.close();
            }
        });

        @SuppressWarnings("unused")
        final int fd = handle.open(testName, Constants.O_WRONLY | Constants.O_CREAT, Constants.S_IRWXU);

        eventHandle.start(testName, true);
        handle.rename(testName, newName);

        final long start = System.currentTimeMillis();
        while (!gotCallback.get() || !gotClose.get()) {
            if (System.currentTimeMillis() - start > TIMEOUT) {
                Assert.fail("timeout waiting for file event");
            }
            loop.runNoWait();
        }

        Assert.assertTrue(gotCallback.get());
        Assert.assertTrue(gotClose.get());
        Assert.assertEquals(times.get(), 1);
    }

    public static void main(final String[] args) throws Exception {
        final FileEventWatchHandleTest test = new FileEventWatchHandleTest();
        test.testFileChangeEvent();
        test.testFileRenameEvent();
    }

}
