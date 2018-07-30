package com.one;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Created by huangyifei on 2018/7/29.
 */
public class DistributedLock implements Lock, Watcher {

    private ZooKeeper zk = null;
    private String ROOT_LOCK = "/locks";//定义根结点
    private String CURRENT_LOCK;//当前的锁
    private String WAIT_LOCK;//正在等待的锁

    private CountDownLatch countDownLatch;

    public DistributedLock() {
        try {
            zk = new ZooKeeper("35.185.164.128:2181", 4000, this);
            //检查根节点是否存在
            Stat stat = zk.exists(ROOT_LOCK,false);
            if (stat == null) {
                //若根结点不存在，则新增一个开放权限的持久节点
                zk.create(ROOT_LOCK,"0".getBytes(),ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean tryLock() {
        //服务调用时在根节点下以创建自己的有序锁节点，
        // 并检查自己是否根节点下最小的锁节点，若是，刚获得锁，
        // 否则设置自己的WAIT_LOCK
        try {
            CURRENT_LOCK = zk.create(ROOT_LOCK + "/", "0".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println(Thread.currentThread().getName() + "->" + CURRENT_LOCK + "尝试竞争锁ING");

            List<String> childs = zk.getChildren(ROOT_LOCK,false);//获取根节点下的所有子节点
            SortedSet<String> sortedSet = new TreeSet<>();
            for (String child : childs) {
                sortedSet.add(ROOT_LOCK + "/" + child);
            }
            //通过sortSet获取比当前锁节点小的所有节点
            SortedSet<String> lessThenMe = ((TreeSet<String>) sortedSet).headSet(CURRENT_LOCK);
            //如果当前节点是最小节点，则马上获得锁
            if (CURRENT_LOCK.equals(sortedSet.first())) {
                return true;
            }
            //若比当前节点小的集合非空，刚取出当前节点的前一节点保存到WATI_LOCK
            if (!lessThenMe.isEmpty()) {
                WAIT_LOCK = lessThenMe.last();
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void lock() {
        if (this.tryLock()) {
            System.out.println(Thread.currentThread().getName() + "->" + CURRENT_LOCK + "获取锁成功");
            return;
        }
        try {
            waitForLock(WAIT_LOCK);//没有获得到锁，阻塞等待锁
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean waitForLock(String WAIT_LOCK) throws KeeperException, InterruptedException {
        Stat stat = zk.exists(WAIT_LOCK, true);
        //若WAIT_LOCK锁节点存在，则阻塞等待锁
        if (stat != null ) {
            System.out.println(Thread.currentThread().getName() + "->等待" + WAIT_LOCK + "释放");
            countDownLatch = new CountDownLatch(1);
            countDownLatch.await();
            System.out.println(Thread.currentThread().getName()+"获取锁成功");
        }
        return true;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        System.out.println(Thread.currentThread().getName() + "释放锁" + ROOT_LOCK + "/" + CURRENT_LOCK);
        try {
            zk.delete(CURRENT_LOCK,-1);
            CURRENT_LOCK = null;
            zk.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        System.out.println(watchedEvent.getType());
        if (this.countDownLatch != null && watchedEvent.getType() !=Event.EventType.None) {
            this.countDownLatch.countDown();
        }
    }
}
