import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.Arrays;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

public class SyncPrimitive implements Watcher {

    static ZooKeeper zk = null;
    static Integer mutex;

    String root;

    SyncPrimitive(String address) {
        if(zk == null){
            try {
                System.out.println("Starting ZK:");
                zk = new ZooKeeper(address, 3000, this);
                mutex = new Integer(-1);
                System.out.println("Finished starting ZK: " + zk);
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
        //else mutex = new Integer(-1);
    }

    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            //System.out.println("Process: " + event.getType());
            mutex.notify();
        }
    }

    /**
     * Barrier
     */
    static public class Barrier extends SyncPrimitive {
        int size;
        String name;

        /**
         * Barrier constructor
         *
         * @param address
         * @param root
         * @param size
         */
        Barrier(String address, String root) {
            super(address);
            this.root = root;
            this.size = 4;

            // Create barrier node
            if (zk != null) {
                try {
                    Stat s = zk.exists(root, false);
                    if (s == null) {
                        zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out
                            .println("Keeper exception when instantiating queue: "
                                    + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted exception");
                }
            }

            // My node name
            try {
                name = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());
            } catch (UnknownHostException e) {
                System.out.println(e.toString());
            }

        }

        /**
         * Join barrier
         *
         * @return
         * @throws KeeperException
         * @throws InterruptedException
         */

        boolean enter() throws KeeperException, InterruptedException{
            zk.create(root + "/" + name, new byte[0], Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL);
            while (true) {
                synchronized (mutex) {
                    List<String> list = zk.getChildren(root, true);

                    if (list.size() < size) {
                        mutex.wait();
                    } else {
                        return true;
                    }
                }
            }
        }

        /**
         * Wait until all reach barrier
         *
         * @return
         * @throws KeeperException
         * @throws InterruptedException
         */

        boolean leave() throws KeeperException, InterruptedException{
            zk.delete(root + "/" + name, 0);
            while (true) {
                synchronized (mutex) {
                    List<String> list = zk.getChildren(root, true);
                        if (list.size() > 0) {
                            mutex.wait();
                        } else {
                            return true;
                        }
                    }
                }
        }
    }

    /**
     * Producer-Consumer queue
     */
    static public class Queue extends SyncPrimitive {

        /**
         * Constructor of producer-consumer queue
         *
         * @param address
         * @param name
         */
        Queue(String address, String name) {
            super(address);
            this.root = name;
            // Create ZK node name
            if (zk != null) {
                try {
                    Stat s = zk.exists(root, false);
                    if (s == null) {
                        zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out.println("Keeper exception when instantiating queue: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted exception");
                }
            }
        }

        /**
         * Add element to the queue.
         *
         * @param i
         * @return
         */

        boolean produce(int i) throws KeeperException, InterruptedException{
            ByteBuffer b = ByteBuffer.allocate(4);
            byte[] value;

            // Add child with value i
            b.putInt(i);
            value = b.array();
            zk.create(root + "/element", value, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);

            return true;
        }


        /**
         * Remove first element from the queue.
         *
         * @return
         * @throws KeeperException
         * @throws InterruptedException
         */
        int consume() throws KeeperException, InterruptedException{
            int retvalue = -1;
            Stat stat = null;

            // Get the first element available
            while (true) {
                synchronized (mutex) {
                    List<String> list = zk.getChildren(root, true);
                    if (list.size() == 0) {
                        System.out.println("Going to wait");
                        mutex.wait();
                    } else {
                        Integer min = new Integer(list.get(0).substring(7));
                        System.out.println("List: "+list.toString());
                        String minString = list.get(0);
                        for(String s : list){
                            Integer tempValue = new Integer(s.substring(7));
                            //System.out.println("Temp value: " + tempValue);
                            if(tempValue < min) { 
                            	min = tempValue;
                            	minString = s;
                            }
                        }
                       System.out.println("Temporary value: " + root +"/"+ minString);
                        byte[] b = zk.getData(root +"/"+ minString,false, stat);
                        //System.out.println("b: " + Arrays.toString(b)); 	
                        zk.delete(root +"/"+ minString, 0);
                        ByteBuffer buffer = ByteBuffer.wrap(b);
                        retvalue = buffer.getInt();
                        return retvalue;
                    }
                }
            }
        }
    }

    static public class Lock extends SyncPrimitive {
    	Tarefa tarefa;
	    String pathName;
      boolean terminou;
    	 /**
         * Constructor of lock
         *
         * @param address
         * @param name Name of the lock node
         */
        Lock(String address, String name, Tarefa tarefa) {
            super(address);
            this.root = name;
            this.tarefa = tarefa;
            this.terminou = false;
            // Create ZK node name
            if (zk != null) {
                try {
                    Stat s = zk.exists(root, false);
                    if (s == null) {
                        zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out.println("Keeper exception when instantiating queue: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted exception");
                }
            }
        }
        
        boolean lock() throws KeeperException, InterruptedException{
            //Step 1
            pathName = zk.create(root + "/lock-", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println("My path name is: "+pathName);
            //Steps 2 to 5
            return testMin();
        }
        
    	boolean testMin() throws KeeperException, InterruptedException{
	    while (true) {
		 Integer suffix = new Integer(pathName.substring(18));
	         //Step 2 
            	 List<String> list = zk.getChildren(root, false);
                 Integer min = new Integer(list.get(0).substring(5));
                 System.out.println("List: "+list.toString());
                 String minString = list.get(0);
                 for(String s : list){
                 	Integer tempValue = new Integer(s.substring(5));
                 	//System.out.println("Temp value: " + tempValue);
                 	if(tempValue < min)  {
                 		min = tempValue;
                 		minString = s;
                 	}
                 }
                System.out.println("Suffix: "+suffix+", min: "+min);
           	//Step 3
             	if (suffix.equals(min)) {
            		System.out.println("Lock acquired for "+minString+"!");
            		return true;
            	}
            	//Step 4
            	//Wait for the removal of the next lowest sequence number
            	Integer max = min;
            	String maxString = minString;
            	for(String s : list){
            		Integer tempValue = new Integer(s.substring(5));
            		//System.out.println("Temp value: " + tempValue);
            		if(tempValue > max && tempValue < suffix)  {
            			max = tempValue;
            			maxString = s;
            		}
            	}
            	//Exists with watch
            	Stat s = zk.exists(root+"/"+maxString, this);
            	System.out.println("Watching "+root+"/"+maxString);
            	//Step 5
            	if (s != null) {
            	    //Wait for notification
            	    break;  
            	}
	    }
            System.out.println(pathName+" is waiting for a notification!");
	    return false;
	}

        synchronized public void process(WatchedEvent event) {
            synchronized (mutex) {
            	String path = event.getPath();
            	if (event.getType() == Event.EventType.NodeDeleted) {
            		System.out.println("Notification from "+path);
			try {
			    if (testMin()) { //Step 5 (cont.) -> go to step 2 to check
				this.compute();
			    } else {
				System.out.println("Not lowest sequence number! Waiting for a new notification.");
			    }
			} catch (InterruptedException e) {
      
      } catch (Exception e) {
        e.printStackTrace();
      }
            	}
            }
        }
        
        void compute() {
        	System.out.println("Lock acquired!");
    		try {
				this.tarefa.leInput();
        System.out.println("Lock released!");
        zk.delete(this.pathName, -1);
			} catch (InterruptedException e) {
      
      } catch (Exception e) {
        e.printStackTrace();
      }
    		
        }

      boolean existe() {
        boolean ret = false;
        try {
          ret = zk.exists(this.pathName, false) != null;
        } catch (InterruptedException e) {
      
        } catch (Exception e) {
          e.printStackTrace();
        }
        return ret;
      }
    }

    public static void queueTest(String args[]) {
        Queue q = new Queue(args[1], "/app3");

        System.out.println("Input: " + args[1]);
        int i;
        Integer max = new Integer(args[2]);

        if (args[3].equals("p")) {
            System.out.println("Producer");
            for (i = 0; i < max; i++)
                try{
                    q.produce(10 + i);
                } catch (KeeperException e){
                    e.printStackTrace();
                } catch (InterruptedException e){
			    e.printStackTrace();
                }
        } else {
            System.out.println("Consumer");

            for (i = 0; i < max; i++) {
                try{
                    int r = q.consume();
                    System.out.println("Item: " + r);
                } catch (KeeperException e){
                    i--;
                } catch (InterruptedException e){
			    e.printStackTrace();
                }
            }
        }
    }

    public static void rodaBarrier(Tarefa t) {
        Barrier b = new Barrier("localhost", "/b1");
        try{
            t.processa();
            Thread.sleep(1000);

            System.out.println(t + " entrou na barrier");
            boolean flag = b.enter();
            System.out.println("Entered barrier: " + 4);
            if(!flag) System.out.println("Error when entering the barrier");
        } catch (InterruptedException e) {
      
        } catch (Exception e) {
          e.printStackTrace();
        }
        try{
            b.leave();
        } catch (KeeperException e){

        } catch (InterruptedException e){

        }
        System.out.println(t + " saiu da barrier");
    }
    
    public static void rodaLock(Tarefa t) {
    	Lock lock = new Lock("localhost","/lockProjeto", t);
        try{
        	boolean success = lock.lock();
        	if (success) {
        		lock.compute();
        	} else {
        		while(lock.existe()) {
        		}
          }
          
          System.out.println("=====================Tarefa " + t + " saindo do lock!");     
        } catch (KeeperException e){
        	e.printStackTrace();
        } catch (InterruptedException e){
        	e.printStackTrace();
        }
    }
    
}
