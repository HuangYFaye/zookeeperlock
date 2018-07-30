package com.one;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.concurrent.CountDownLatch;

/**
 * Created by huangyifei on 2018/7/29.
 */
public class CuratorDemo {
    public static void main(String[] args) {
        CountDownLatch countDownLatch = new CountDownLatch(10);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder().
                connectString("35.185.164.128:2181").namespace("locks").
                retryPolicy(new ExponentialBackoffRetry(1000, 3)).
                sessionTimeoutMs(4000).build();
        curatorFramework.start();
        InterProcessMutex interProcessMutex = new InterProcessMutex(curatorFramework, "/locks");
        try {

            for (int i = 0; i < 10; i++) {
                new Thread(() -> {
                    try {
                        countDownLatch.await();
                        interProcessMutex.acquire();
                        System.out.println(Thread.currentThread().getName()+"成功获得锁");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },"Thread-"+i).start();
                countDownLatch.countDown();
            }
//            interProcessMutex.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
