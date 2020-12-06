import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.*;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

//import SyncPrimitive;

// distribui as tarefas para a thread pool
class DistribuirTarefas implements Runnable {
	private Socket socket;
	private TCPServer servidor;
	private ExecutorService threadPool;

	public DistribuirTarefas(Socket socket, TCPServer servidor, ExecutorService threadPool) {
		this.socket = socket;
		this.servidor = servidor;
		this.threadPool = threadPool;
	}

	@Override
	public void run() {
		try {
			// Create a BufferedReader object to read strings from the socket. (read strings FROM CLIENT)
			BufferedReader br = new BufferedReader(new InputStreamReader(servidor.server.getInputStream()));
			//Create output stream to write to/send TO CLIENT
			DataOutputStream outStream = new DataOutputStream(servidor.server.getOutputStream());

      try {
        SyncPrimitive.Queue q = new SyncPrimitive.Queue("localhost", "/queueProjeto");

        q.produce(1);
        q.produce(2);
        q.produce(3);

        TarefaTiraEspacos t1 = new TarefaTiraEspacos(br, outStream);
        TarefaReverter t2 = new TarefaReverter(br, outStream);
        TarefaMaiuscula t3 = new TarefaMaiuscula(br, outStream);

        for(int i = 0; i < 3; i++) {
          int tarefa = q.consume();

          switch(tarefa) {
            case 1:
              threadPool.execute(t1);
              break;
            case 2:
              threadPool.execute(t2);
              break;
            case 3:
              threadPool.execute(t3);
              break;
            default:
              break;
          }
        }

        SyncPrimitive.rodaBarrier(new TarefaMain());

        ZooKeeper zk = SyncPrimitive.zk;

        for(String filho : zk.getChildren("/b1", false)) {
          zk.delete("/b1/"+filho, 0);
        }

        String resp = t1.getSaida() + t2.getSaida() + t3.getSaida();
        System.out.println("\n\n\n"+resp);

        outStream.writeBytes(resp);
      } catch (InterruptedException e) {
        
      } catch (Exception e) {
        e.printStackTrace();
      }

			System.out.println("\n\n\n\n\nConnection closed from "+ servidor.server.getInetAddress().getHostAddress()+":"+servidor.server.getPort());
			//Close current connection
			br.close();
			outStream.close();
			servidor.server.close();
      // System.exit(0);
		} catch (IOException e) {
			//Print exception info
			e.printStackTrace();
		} 

    threadPool.shutdownNow();
	}
}

abstract class Tarefa implements Runnable {
  private BufferedReader entradaCliente;
  private DataOutputStream saidaCliente;
  private String input;
  private String saida;

  public Tarefa(BufferedReader entradaCliente, DataOutputStream saidaCliente) {
    this.entradaCliente = entradaCliente;
    this.saidaCliente = saidaCliente;
    this.input = null;
    this.saida = null;
  }

  public DataOutputStream getSaidaCliente() {
    return this.saidaCliente;
  }

  public BufferedReader getEntrada() {
    return this.entradaCliente;
  }

  public String getInput() {
    return this.input;
  }

  public String getSaida() {
    return this.saida;
  }
  
  public void setInput(String novo) {
    this.input = novo;
  }

  public void setSaida(String nova) {
    this.saida = nova;
  }

	public abstract void run();
  public abstract void leInput() throws IOException, KeeperException, InterruptedException;
  public abstract void processa() throws IOException, KeeperException, InterruptedException;
}

class TarefaTiraEspacos extends Tarefa {
  public TarefaTiraEspacos(BufferedReader entradaCliente, DataOutputStream saidaCliente) {
    super(entradaCliente, saidaCliente);
  }

  public void run() {
    try {
      System.out.println("----------------------------TarefaTiraEspacos: Entrando no lock");//this.setInput("frase tira espaco");
      SyncPrimitive.rodaLock(this);
      SyncPrimitive.rodaBarrier(this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void leInput() throws IOException, KeeperException, InterruptedException {
    this.getSaidaCliente().writeBytes("Digite uma frase para remover os espacos:\n");
    this.setInput(this.getEntrada().readLine());
  }

  public void processa() throws IOException, KeeperException, InterruptedException {
    this.setSaida(this.getInput().replaceAll("\\s+","") + "\n");
  }
}

class TarefaMaiuscula extends Tarefa {
  public TarefaMaiuscula(BufferedReader entradaCliente, DataOutputStream saidaCliente) {
    super(entradaCliente, saidaCliente);
  }

  public void run() {
    try {
      System.out.println("----------------------------TarefaMaiuscula: Entrando no lock");//this.setInput("frase maiuscula");
      SyncPrimitive.rodaLock(this);
      SyncPrimitive.rodaBarrier(this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void leInput() throws IOException, KeeperException, InterruptedException {
    this.getSaidaCliente().writeBytes("Digite uma frase para colocar em caixa alta:\n");
    this.setInput(this.getEntrada().readLine());
  }

  public void processa() throws IOException, KeeperException, InterruptedException {
    this.setSaida(this.getInput().toUpperCase() + "\n");
  }
}

class TarefaReverter extends Tarefa {
  public TarefaReverter(BufferedReader entradaCliente, DataOutputStream saidaCliente) {
    super(entradaCliente, saidaCliente);
  }

  public void run() {
    try {
      System.out.println("----------------------------TarefaReverter: Entrando no lock");//this.setInput("frase reverter");
      SyncPrimitive.rodaLock(this);
      SyncPrimitive.rodaBarrier(this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void leInput() throws IOException, KeeperException, InterruptedException {
    this.getSaidaCliente().writeBytes("Digite uma frase para ser revertida:\n");
    this.setInput(this.getEntrada().readLine());
  }

  public void processa() throws IOException, KeeperException, InterruptedException {
    StringBuilder strb = new StringBuilder(this.getInput());
    this.setSaida(strb.reverse().toString() + "\n");
  }
}

class TarefaMain extends Tarefa {
  public TarefaMain() {
    super(null, null);
  }

  public void run() {}

  public void leInput() throws IOException, KeeperException, InterruptedException {
    this.setInput("");
  }

  public void processa() throws IOException, KeeperException, InterruptedException {
    this.setSaida("");
  }
}

// Usada pelo threadpool para criar novos threads
class FabricaDeThreads implements ThreadFactory {
	private static int numeroThread = 1;

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r, "Thread Servidor " + numeroThread++);
		thread.setUncaughtExceptionHandler(new TratadorDeExcecao());
		
		return thread;
	}
}

// tradador de excecao que sera associado a cada thread criada pela thread pool
class TratadorDeExcecao implements UncaughtExceptionHandler {

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		System.out.println("Deu erro na thread " + t.getName());
    e.printStackTrace();
	}
}

public class TCPServer extends Thread {
	/** Well-known server port. */
	public static int serverPort = 9000;

	/** Client socket for the thread. */
	Socket server;
	
	/**
	 * Creates a new TCPServer worker thread.
	 * @param server The client socket for this object.
	 */
	public TCPServer (Socket server, int numTarefas){
		this.server = server;	
	}
	
	public void run() {
    ExecutorService threadPool = Executors.newCachedThreadPool(new FabricaDeThreads());
		threadPool.execute(new DistribuirTarefas(server, this, threadPool));
	}
	
	public static void main (String args[]) {
    try {
      //Dispatcher socket
      ServerSocket serverSocket = new ServerSocket(serverPort);
      //Waits for a new connection. Accepts connection from multiple clients
      while (true) {
        System.out.println("Waiting for connection at port "+serverPort+".");
        //Worker socket 
        Socket s = serverSocket.accept();
        System.out.println("Connection established from " + s.getInetAddress().getHostAddress() + ", local port: "+s.getLocalPort()+", remote port: "+s.getPort()+".");
        //Invoke the worker thread
        TCPServer worker = new TCPServer(s, 2);
        worker.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
	}
}