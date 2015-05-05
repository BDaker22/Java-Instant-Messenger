package seis635.project.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import seis635.project.cmn.Message;

public class ChatServer {

	final static int portNumber = 8001;
	private static ServerSocket serverSocket;
	private static InetAddress IP;
	private static Socket clientSocket;
	public static Map<String, CSHandler> users;
	public static Queue<Message> msgQueue;
	
	private static CSView view;
	private static CSController controller;
	
	public ChatServer(){
		
		controller = new CSController();
		view = new CSView(controller);
		controller.setCSView(view);
		
		users = new HashMap<String, CSHandler>();
		msgQueue = new LinkedList<Message>();
		
		init();				//Initialize the Chat Server
		
		Listener serverListener = new Listener();
		serverListener.start();
		
		while(true){
			listen();		//Listen on port indefinitely
		}
		
	}
	
	public static void init(){
		try{
			//Start server
			IP = InetAddress.getLocalHost();
			serverSocket = new ServerSocket(portNumber);
			controller.writeMsg("Server running on " + IP + ":" + portNumber);
			
			//Debugging on console
			System.out.println("Server running on " + IP + ":" + portNumber);
		} catch(Exception e){
			System.err.println("Server could not be started.");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	//The server listens and calls the method to log in new users
	public static void listen(){
		
		try{
			clientSocket = serverSocket.accept();
			controller.writeMsg("Client IP " + clientSocket.getLocalAddress().getHostAddress()
					+ " connected.");
			
			//Debugging on console
			System.out.println("Client IP " + clientSocket.getLocalAddress().getHostAddress()
					+ " connected.");
			
			loginUser(clientSocket);
			
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public static void loginUser(Socket clientSocket) throws IOException{

		try{
			ObjectInputStream objIn = new ObjectInputStream(clientSocket.getInputStream());
			ObjectOutputStream objOut = new ObjectOutputStream(clientSocket.getOutputStream());
			Message loginRequest = (Message) objIn.readObject();
			
			if(loginRequest.getType() == Message.LOGIN && !loginRequest.getData().equals("")){
				
				//Create new handler, log user in HashMap
				CSHandler handler = new CSHandler(clientSocket, objIn, objOut);
				users.put((String) loginRequest.getData(), handler);
				controller.writeMsg("User " + loginRequest.getData() + " logged in");
				
				//Confirm login to user
				objOut.writeObject(new Message(null, null, "SUCCESS", Message.SERVER_RESPONSE));
				objOut.flush();
				
				//Send current users to update GUI initially
				//TODO - send updated users to ALL USERS
				//objOut.writeObject(new Message(null, null, getUsernames(), Message.UPDATE_USERS));
				//objOut.flush();
				updateAllUsers();
				
				//Update users on Server GUI
				view.updateUsers(getUsernames());
			}
			else{
				objOut.writeObject(new Message(null, null, "FAIL", Message.SERVER_RESPONSE));
				objOut.flush();
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();	
		}
	}
	
	public static String[] getUsernames(){
		return users.keySet().toArray(new String[users.size()]);
	}
	
	public static void updateAllUsers(){
		for(String user : users.keySet()){
			users.get(user).sendMessage(new Message(null, null, getUsernames(), Message.UPDATE_USERS));
		}
	}
	
	public void removeUser(String user){
		users.remove(user);
		//need method to update all users
	}
	
	public static void main(String[] args){
		
		@SuppressWarnings("unused")
		ChatServer server = new ChatServer();
	}
	
	
	//Update 5/5/2015: Create member class as helper for ChatServer.  This 
	//class will contain the Thread listening on the Port and will have access 
	//to all the ChatServer's incoming data.  The Listener will poll a message 
	//queue.  Many synchronization issues can effectively be solved by
	//synchronizing this member class's run method and passing all Message objects
	//through it.
	public class Listener extends Thread {
		
		public synchronized void run(){
			try{
				while(true){
					while(msgQueue.peek() == null){
						wait();	//Need a value here?
					}
					
					Message currentMsg = msgQueue.poll();
					
					for(String user : users.keySet()){
						if(user.equals(currentMsg.getRecipient())){
							users.get(user).sendMessage(currentMsg);
						}
					}
				}
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
