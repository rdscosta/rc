import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
	
	//Leitura
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );    
    String hostname = "localhost";
    Socket clientSocket;
	//Método para imprimir mensagem
    PrintWriter out;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String input_message) {
    	
    	String message = input_message.replaceAll("(\\r|\\n)", "");
    	
    	String[] parsed = message.split(" ");
    	String toAppend = message;
    	
    	if(parsed[0].equals("MESSAGE")){
    		toAppend = "";
    		toAppend = parsed[1] + ": ";
    		String tempString = "MESSAGE " + parsed[1];
    		for (int i = tempString.length(); i < message.length(); i++) {
    			toAppend = toAppend + message.charAt(i);
    		}
    	}
    	if(parsed[0].equals("BYE")){
    		toAppend = "";
    		toAppend = "You have left the server. Cya soon! :D";
    	}
    	if(parsed[0].equals("LEFT")){
    		toAppend = "";
    		toAppend = "User " + parsed[1] + " has left the channel";
    	}
    	if(parsed[0].equals("JOINED")){
    		toAppend = "";
    		toAppend = "User " + parsed[1] + " has joined the channel";
    	}
    	if(parsed[0].equals("NEWNICK")){
    		toAppend = "";
    		toAppend = "User " + parsed[1] + " mudou de nome para " + parsed[2];
    	}
    	if(parsed[0].equals("PRIVATE")){
    		toAppend = "";
    		toAppend = parsed[1] + "(priv): ";
    		String tempString = "MESSAGE " + parsed[1];
    		for (int i = tempString.length(); i < message.length(); i++) {
    			toAppend = toAppend + message.charAt(i);
    		}
    	}
    	
        chatArea.append(toAppend + '\n');
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        clientSocket = new Socket( server , port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
		
    	out.println(message);
    
	}

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
		
		//BufferedReader recebe dados inseridos do servidor --> String response recebe a totalidade do Server_IN --> chega ao fim, usa método printMessage.
    	BufferedReader Server_IN = null;
    	Server_IN = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    	String response;
    	while ((response = (String) Server_IN.readLine()) != null) 
        {
            printMessage(response + '\n');
        }
    	
    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        //ChatClient client = new ChatClient("asd",22);
        client.run();
    }

}