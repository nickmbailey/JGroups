package org.jgroups.blocks;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.protocols.CENTRAL_LOCK;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.tests.ChannelTestBase;
import org.jgroups.util.Util;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests {@link org.jgroups.blocks.locking.LockService}
 * @author Bela Ban
 */
@Test(groups=Global.STACK_DEPENDENT,sequential=true)
public class LockServiceTest extends ChannelTestBase {
    protected JChannel c1, c2, c3;
    protected LockService s1, s2, s3;
    protected Lock lock;
    protected static final String LOCK="sample-lock";


    @BeforeClass
    protected void init() throws Exception {
        c1=createChannel(true, 3, "A");
        addLockingProtocol(c1);
        s1=new LockService(c1);
        c1.connect("LockServiceTest");

        c2=createChannel(c1, "B");
        s2=new LockService(c2);
        c2.connect("LockServiceTest");

        c3=createChannel(c1, "C");
        s3=new LockService(c3);
        c3.connect("LockServiceTest");
        
        lock=s1.getLock(LOCK);
    }


    @AfterClass
    protected void cleanup() {
        Util.close(c3,c2,c1);
    }

    @BeforeMethod
    protected void unlockAll() {
        s3.unlockAll();
        s2.unlockAll();
        s1.unlockAll();
    }


    public void testSimpleLock() {
        lock(lock, LOCK);
        unlock(lock, LOCK);
    }

    public void testLockingOfAlreadyAcquiredLock() {
        lock(lock, LOCK);
        lock(lock, LOCK);
        unlock(lock, LOCK);
    }

    public void testUnsuccessfulTryLock() {
        System.out.println("s1:\n" + s1.printLocks() +
                             "\ns2:\n" + s2.printLocks() +
                             "\ns3:\n" + s3.printLocks());


        Lock lock2=s2.getLock(LOCK);
        lock(lock2, LOCK);
        try {
            boolean rc=tryLock(lock, LOCK);
            assert !rc;
            unlock(lock, LOCK);
        }
        finally {
            unlock(lock2, LOCK);
        }
    }

    public void testUnsuccessfulTryLockTimeout() throws InterruptedException {
        Lock lock2=s2.getLock(LOCK);
        lock(lock2, LOCK);
        try {
            boolean rc=tryLock(lock, 1000, LOCK);
            assert !rc;
        }
        finally {
            unlock(lock2, LOCK);
        }
    }


    public void testLockInterrupt() {
        // Interrupt ourselves before trying to acquire lock
        Thread.currentThread().interrupt();

        lock.lock();
        try {
            System.out.println("Locks we have: " + s1.printLocks());
            if(Thread.interrupted()) {
                System.out.println("We still have interrupt flag set, as it should be");
            }
            else {
                assert false : "Interrupt status was lost - we don't want this!";
            }
        }
        finally {
            lock.unlock();
        }
    }

     public void testTryLockInterrupt() {
        // Interrupt ourselves before trying to acquire lock
        Thread.currentThread().interrupt();

        lock.tryLock();
        try {
            System.out.println("Locks we have: " + s1.printLocks());
            if(Thread.interrupted()) {
                System.out.println("We still have interrupt flag set, as it should be");
            }
            else {
                assert false : "Interrupt status was lost - we don't want this!";
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Test(expectedExceptions=InterruptedException.class)
    public void testLockInterruptibly() throws InterruptedException {
        // Interrupt ourselves before trying to acquire lock
        Thread.currentThread().interrupt();

        lock.lockInterruptibly();
        try {
            System.out.println("Locks we have: " + s1.printLocks());
            if(Thread.interrupted()) {
                System.out.println("We still have interrupt flag set, as it should be");
            }
            else {
                assert false : "Interrupt status was lost - we don't want this!";
            }
        }
        finally {
            lock.unlock();
        }
    }


    public void testSuccessfulSignalAllTimeout() throws InterruptedException, BrokenBarrierException {
        Lock lock2=s2.getLock(LOCK); 
        Thread locker=new Signaller(true);
        boolean rc=tryLock(lock2, 5000, LOCK);
        assert rc;
        locker.start();
        assert awaitNanos(lock2.newCondition(), TimeUnit.SECONDS.toNanos(5), LOCK) > 0 : "Condition was not signalled";
        unlock(lock2, LOCK);
    }


    public void testSuccessfulTryLockTimeout() throws InterruptedException, BrokenBarrierException {
        final CyclicBarrier barrier=new CyclicBarrier(2);
        Thread locker=new Locker(barrier);
        locker.start();
        barrier.await();
        boolean rc=tryLock(lock, 10000, LOCK);
        assert rc;
        unlock(lock, LOCK);
    }


    public void testConcurrentLockRequests() throws Exception {
        int NUM=10;
        final CyclicBarrier barrier=new CyclicBarrier(NUM +1);
        TryLocker[] lockers=new TryLocker[NUM];
        for(int i=0; i < lockers.length; i++) {
            lockers[i]=new TryLocker(lock, barrier, 500);
            lockers[i].start();
        }
        barrier.await();
        for(TryLocker locker: lockers)
            locker.join();
        int num_acquired=0;
        for(TryLocker locker: lockers) {
            if(locker.acquired) {
                num_acquired++;
            }
        }
        assert num_acquired == 1;
    }

    public void testConcurrentLockRequestsFromDifferentMembers() throws Exception {
        int NUM=10;
        final CyclicBarrier barrier=new CyclicBarrier(NUM +1);
        TryLocker[] lockers=new TryLocker[NUM];
        LockService[] services=new LockService[]{s1, s2, s3};

        for(int i=0; i < lockers.length; i++) {
            Lock mylock=services[i % services.length].getLock(LOCK);
            lockers[i]=new TryLocker(mylock, barrier, 500);
            lockers[i].start();
        }
        barrier.await();
        for(TryLocker locker: lockers)
            locker.join();
        int num_acquired=0;
        for(TryLocker locker: lockers) {
            if(locker.acquired) {
                num_acquired++;
            }
        }
        assert num_acquired == 1;
    }



    
    protected class Locker extends Thread {
        protected final CyclicBarrier barrier;

        public Locker(CyclicBarrier barrier) {
            this.barrier=barrier;
        }

        public void run() {
            lock(lock, LOCK);
            try {
                barrier.await();
                Util.sleep(500);
            }
            catch(Exception e) {
            }
            finally {
                unlock(lock, LOCK);
            }
        }
    }
    
    protected class Signaller extends Thread {
        protected final boolean all;

        public Signaller(boolean all) {
            this.all=all;
        }

        public void run() {
            lock(lock, LOCK);
            try {
                Util.sleep(500);
                
                if (all) {
                    signallingAll(lock.newCondition(), LOCK);
                }
                else {
                    signalling(lock.newCondition(), LOCK);
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
            finally {
                unlock(lock, LOCK);
            }
        }
    }

    protected static class TryLocker extends Thread {
        protected final Lock mylock;
        protected final CyclicBarrier barrier;
        protected final long timeout;
        protected boolean acquired;

        public TryLocker(Lock mylock, CyclicBarrier barrier, long timeout) {
            this.mylock=mylock;
            this.barrier=barrier;
            this.timeout=timeout;
        }

        public boolean isAcquired() {
            return acquired;
        }

        public void run() {
            try {
                barrier.await();
            }
            catch(Exception e) {
                e.printStackTrace();
            }

            try {
                acquired=tryLock(mylock, timeout, LOCK);
                Util.sleep(timeout * 2);
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                unlock(mylock, LOCK);
            }
        }
    }


    protected static void lock(Lock lock, String name) {
        System.out.println("[" + Thread.currentThread().getId() + "] locking " + name);
        lock.lock();
        System.out.println("[" + Thread.currentThread().getId() + "] locked " + name);
    }

    protected static boolean tryLock(Lock lock, String name) {
        System.out.println("[" + Thread.currentThread().getId() + "] tryLocking " + name);
        boolean rc=lock.tryLock();
        System.out.println("[" + Thread.currentThread().getId() + "] " + (rc? "locked " : "failed locking") + name);
        return rc;
    }

    protected static boolean tryLock(Lock lock, long timeout, String name) throws InterruptedException {
        System.out.println("[" + Thread.currentThread().getId() + "] tryLocking " + name);
        boolean rc=lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        System.out.println("[" + Thread.currentThread().getId() + "] " + (rc? "locked " : "failed locking ") + name);
        return rc;
    }

    protected static void unlock(Lock lock, String name) {
        if(lock == null)
            return;
        System.out.println("[" + Thread.currentThread().getId() + "] releasing " + name);
        lock.unlock();
        System.out.println("[" + Thread.currentThread().getId() + "] released " + name);
    }
    
    protected static long awaitNanos(Condition condition, long nanoSeconds, 
                                     String name) throws InterruptedException {
        System.out.println("[" + Thread.currentThread().getId() + "] waiting for signal - released lock " + name);
        long value = condition.awaitNanos(nanoSeconds);
        System.out.println("[" + Thread.currentThread().getId() + "] waited for signal - obtained lock " + name);
        return value;
    }
    
    protected static void signalling(Condition condition, String name) {
        System.out.println("[" + Thread.currentThread().getId() + "] signalling " + name);
        condition.signal();
        System.out.println("[" + Thread.currentThread().getId() + "] signalled " + name);
    }
    
    protected static void signallingAll(Condition condition, String name) {
        System.out.println("[" + Thread.currentThread().getId() + "] signalling all " + name);
        condition.signalAll();
        System.out.println("[" + Thread.currentThread().getId() + "] signalled " + name);
    }

    protected void addLockingProtocol(JChannel ch) {
        ProtocolStack stack=ch.getProtocolStack();
        Protocol lockprot = new CENTRAL_LOCK();
        lockprot.setLevel("trace");
        stack.insertProtocolAtTop(lockprot);
    }
}
