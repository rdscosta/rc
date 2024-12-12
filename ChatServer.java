import java.awt.TrayIcon.MessageType;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;



public class ChatServer{
	// Buffer para receber os dados
	static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

	// Decoder para texto recebido, assumindo que está em formato utf-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetDecoder decoder = charset.newDecoder();
	
	public static Hashtable<SocketChannel, String> users = new Hashtable<SocketChannel, String>();
	public static Hashtable<SocketChannel, Integer> userState = new Hashtable<SocketChannel, Integer>();
	public static Hashtable<SocketChannel, String> userChannel = new Hashtable<SocketChannel, String>();
	
	//Variáveis de estado para utilizadores (INSIDE, OUTSIDE, etc.) & códigos de ERRO / OK
	public static final Integer INIT = 1;
	public static final Integer OUTSIDE = 2;
	public static final Integer INSIDE = 3;
	public static final String OK = "OK" + '\n';
	public static final String ERROR = "ERROR" + '\n';
	
	public static String command = null;
	
	static public void main( String args[] ) throws Exception {
		// Recebemos port da linha de comandos
		int port = Integer.parseInt( args[0] );
		
		try {
			// CriaServerSocketChannel
			ServerSocketChannel ssc = ServerSocketChannel.open();

			// non-blocking, para permitir uso de Select
			ssc.configureBlocking( false );

			// Liga o socket conectado a este canal ao port listado
			ServerSocket ss = ssc.socket();
			InetSocketAddress isa = new InetSocketAddress( port );
			ss.bind( isa );

			// Cria selector
			Selector selector = Selector.open();

			// Regista o canal, para escutar conexões
			ssc.register( selector, SelectionKey.OP_ACCEPT );
			System.out.println("Listening on port "+ port );

			while (true) {
				// Verifica atividade
				int num = selector.select();

				// Sem atividade, continua no loop
				if (num == 0) {
					continue;
				}

				// Deteta chaves da atividade recebida, e processa
				Set keys = selector.selectedKeys();
				Iterator it = keys.iterator();
				
				while (it.hasNext()) {
					// Get a key representing one of bits of I/O activity
					SelectionKey key = (SelectionKey)it.next();

					// Que tipo de atividade?
					if (key.isAcceptable()) {
						
						// Conexão! Regista o socket com o Selector para ler o input
						Socket s = ss.accept();
						System.out.println( "Got connection from "+s );

						// Non-blocking, para se usar selector
						SocketChannel sc = s.getChannel();
						sc.configureBlocking( false );

						// Regista com o selector para leitura
						sc.register( selector, SelectionKey.OP_READ );
						userState.put(sc, INIT);
					} else if (key.isReadable()) {

						SocketChannel sc = null;

						try {

							// Dados no canal! --> Lê...
							sc = (SocketChannel)key.channel();
							boolean ok = processInput( sc );

							// Se não houver conexão, remove do selector
							if (!ok) {
								key.cancel();
								Socket s = null;
								try {
									s = sc.socket();
									System.out.println( "Closing connection to "+s );
									s.close();
								} catch( IOException ie ) {
									System.err.println( "Error closing socket "+s+": "+ie );
								}
							}

						} catch( IOException ie ) {

							// Com exceção, remove canal do selector
							key.cancel();

							try {
								sc.close();
							} catch( IOException ie2 ) { System.out.println( ie2 ); }

							System.out.println( "Closed "+sc );
						}
					}
				}

				// Após uso das keys, estas são removidas
				keys.clear();
			}
		} catch( IOException ie ) {
			System.err.println( ie );
		}
	}


	// Lê mensagem do socket, e envia para stdout
	static private boolean processInput( SocketChannel sc ) throws IOException {
		//Lê no buffer
		Socket s = sc.socket();
		buffer.clear();
		sc.read( buffer );
		buffer.flip();
		// Sem dados? Fecha conexão
		if (buffer.limit()==0) {
			return false;
		}

		// Decode --> Print para stdout
		String message = decoder.decode(buffer).toString();
		
		if (message.charAt(message.length()-1) == '\n') {
			
			if (command != null) {
				message = command + message;
				command = null;
			}
			
			message = message.replaceAll("(\\r|\\n)", "");
			if(message.equals("")) {
				return true;
			}
			String[] parsed = message.split(" ");
			
			if(parsed.length == 0) {
				return true;
			}
			
			// NICK
			if (parsed[0].equalsIgnoreCase("/nick") && parsed.length == 2) {
				if(users.containsValue(parsed[1])) {
					// Nick existe
					System.out.println("Nick wasn't changed");
					sendMessage(sc, ERROR);
				} else {
					//nick não existe, altera
					userChangedNick(sc, users.get(sc), parsed[1]);
					users.put(sc, parsed[1]);
					if(userState.get(sc) != INSIDE){
						userState.put(sc, OUTSIDE);
					}
					//Imprime informação no servidor		
					System.out.println("User changed nick");
					sendMessage(sc, OK);
					
				}
				return true;
			}
			// JOIN
			if (parsed[0].equalsIgnoreCase("/join") && parsed.length == 2) {
				//Se user está no estado INIT, retorna erro
				if(userState.get(sc).equals(INIT)) {
					sendMessage(sc, ERROR);
					return true;
				}
				//Se user está dentro de uma outra sala, sai dessa
				if(userState.get(sc).equals(INSIDE)) {
					userLeave(sc, userChannel.get(sc));
				}
				//Atribui user a sala
				userChannel.put(sc, parsed[1]);
				userState.put(sc, INSIDE);
				sendMessage(sc, OK);
				
				userJoin(sc, parsed[1]);
				System.out.println("User has joined room " +parsed[1]);
				
				return true;
			}
			// LEAVE
			if (parsed[0].equalsIgnoreCase("/leave")) {
				//Se user está dentro de sala, ele sai
				if(userState.get(sc) == INSIDE) {
					userLeave(sc, userChannel.get(sc));
					userState.put(sc, OUTSIDE);
					userChannel.remove(sc);
					sendMessage(sc, OK);
				} else {
					System.out.println("This user hasn't joined a channel");
					sendMessage(sc, ERROR);
				}
				System.out.println("User has left channel");
				
				return true;
			}
			// BYE
			if (parsed[0].equalsIgnoreCase("/bye")) {
				//Se user está dentro de sala, ele sai dela
				if(userState.get(sc) == INSIDE) {
					userLeave(sc, userChannel.get(sc));
				}
				userState.remove(sc);
				users.remove(sc);
				userChannel.remove(sc);
				String str_temp = "BYE"+'\n';
				sendMessage(sc, str_temp);
				System.out.println("User has left connection");
				
				return false;
			}
			// PRIV
			if (parsed[0].equalsIgnoreCase("/priv")) {
				
				Enumeration keys = users.keys();
				
				SocketChannel sc_temp = null;
				String nick = null;
				//Extrai key de user a que a mensagem privada vai ser enviada, e usa-a para determinar o canal em que deve enviar os dados
				while (keys.hasMoreElements()){	
					//Itera pela lista de keys
					Object key = keys.nextElement();
					sc_temp = (SocketChannel) key;
					//Se encontrar um user com a key igual à inserida no command
					if(users.get(key).equals(parsed[1])) {
						nick = users.get(key);
						break;
					}
				}
				//Nick não inserida
				if(nick == null) {
					sendMessage(sc, ERROR);
					return true;
				}
				//informação no server
				String messageToSend = "PRIVATE " + users.get(sc);
				//Recebe mensagem inserida e adiciona para ser enviada privadamente
				for(int i = 2; i< parsed.length; i++){
					messageToSend = messageToSend + " " + parsed[i];
				}

				System.out.println(messageToSend);
				messageToSend = messageToSend + '\n';
				sendMessage(sc_temp, messageToSend);
				sendMessage(sc, messageToSend);
				return true;
			}
			

			if(parsed[0].charAt(0) == '/') {
				// Not a command
				if(userState.get(sc) == INSIDE) {
					// User dentro de canal, pode enviar mensagens para ele
					String messageToSend = "MESSAGE " + users.get(sc) + " " + message +'\n';
					// Mensagem no servidor
					System.out.println("Not a command");
					
					Enumeration keys = userChannel.keys();
					
					while (keys.hasMoreElements()){	
						Object key = keys.nextElement();
						if(userChannel.get(sc).equals(userChannel.get(key))) {
							SocketChannel sc_temp = (SocketChannel) key;
							sendMessage(sc_temp, messageToSend);
						}
					}
					
					return true;
				}
			}

			// MESSAGE
			if(userState.get(sc) == INSIDE) {
				// User dentro de canal, pode mandar mensagens
				String messageToSend = "MESSAGE " + users.get(sc) + " " + message +'\n';
				
				if(parsed[0].length()>1){
					if(parsed[0].charAt(1) == '/') {
						//Se é um comentário
						System.out.println("String starts with //");
						
						String str_tempString = "";

						for (int i = 1; i < message.length(); i++) {
							str_tempString = str_tempString + message.charAt(i);
						}
						messageToSend = "MESSAGE " + users.get(sc) + " /" + str_tempString +'\n';
					}
				} 
				
				// Mensagem regular
				System.out.println("A normal message");
				
				Enumeration keys = userChannel.keys();
				
				while (keys.hasMoreElements()){	
					Object key = keys.nextElement();
					if(userChannel.get(sc).equals(userChannel.get(key))) {
						SocketChannel sc_temp = (SocketChannel) key;
						sendMessage(sc_temp, messageToSend);
					}
				}
				
				return true;
			}
			else {
				// User isn't in any channel
				sendMessage(sc, ERROR);
			}
		}
		else{
			// message doesn't contain \n
			if(command == null) {
				command = message;
			} 
			else {
				command = command + message;
			}
			
		}
		return true;
	}
	//Envio de mensagens
	public static void sendMessage(SocketChannel sc_to_send, String messageToSend) throws IOException {
		
		buffer.clear();
		buffer.put(messageToSend.getBytes());
		buffer.flip();
		//Escreve todos os dados de messageToSend para o Buffer
		while(buffer.hasRemaining()) {
		    sc_to_send.write(buffer);
		}
	}
	//User junta-se a uma sala
	public static void userJoin(SocketChannel sc_to_send, String channel) throws IOException {
		Enumeration keys = userChannel.keys();
		String tempMessage = "JOINED " + users.get(sc_to_send) + '\n';
		
		while (keys.hasMoreElements()){	
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(channel) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}
	//Saída de User de uma sala
	public static void userLeave(SocketChannel sc_to_send, String channel) throws IOException {
		//Key da sala
		Enumeration keys = userChannel.keys();
		//Mensagem a informar saída de User da sala channel
		String tempMessage = "LEFT " + users.get(sc_to_send) + '\n';
		
		while (keys.hasMoreElements()){	
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(channel) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}
	//Muda NICK antida por uma nova, usando NEWNICK como mensagem
	public static void userChangedNick(SocketChannel sc_to_send, String old_nick, String new_nick) throws IOException {
		Enumeration keys = userChannel.keys();
		
		String tempMessage = "NEWNICK " + old_nick + " "+ new_nick +'\n';
		
		while (keys.hasMoreElements()){	
			Object key = keys.nextElement();
			SocketChannel sc_temp = (SocketChannel) key;
			if(userChannel.get(key).equals(userChannel.get(sc_to_send)) && sc_temp != sc_to_send) {
				sendMessage(sc_temp, tempMessage);
			}
		}
	}
}