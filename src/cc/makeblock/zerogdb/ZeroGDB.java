package cc.makeblock.zerogdb;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;

import javax.swing.*;
import javax.swing.text.DefaultCaret;

import processing.app.Base;
import processing.app.Editor;
import processing.app.Sketch;
import processing.app.tools.Tool;

public class ZeroGDB implements Tool {
	Editor editor;
	File buildFolder;
	File elf;
	// UI component
	private JFrame frame;
	private JTextArea textArea;
	private JTextField input;
	private JButton sendBtn;
	// debug related process
	Process pocd;
	Process pgdb;
	BufferedWriter gdbcmd;
	Handler dbghd;
	
	// console history
	int cmdIndex;
	ArrayList<String> cmdList;
	
	public String getMenuTitle() {
		return "GDB Interface";
	}

	public void init(Editor arg0) {
	    this.editor = arg0;
	}

	public void run() {
	    //String sketchName = editor.getSketch().getName();
	    if(getElfFile()==false) return;
	    showDebugPanel();
	    cmdList = new ArrayList<String>();
	    cmdIndex = -1;
	}
	
	
	boolean getElfFile(){
		Sketch skt = editor.getSketch();
		buildFolder = Base.getBuildFolder();
		String sketchName = editor.getSketch().getName();
		String elfPath = buildFolder.getAbsolutePath()+"/"+sketchName+".cpp.elf";
		elf = new File(elfPath);
		if(elf.exists()){
			return true;
		}else{
			editor.statusError("Can't fild elf "+elf.getPath());
			return false;
		}
		
	}
	
	void showDebugPanel(){
		frame = new JFrame("GDB console");
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);
		// console output
		textArea = new JTextArea(15,50);
		textArea.setWrapStyleWord(true);
		textArea.setEditable(true);
		JScrollPane scroller = new JScrollPane(textArea);
		scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		DefaultCaret caret =  (DefaultCaret)textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		panel.add(scroller);
		// console input
		JPanel inputpanel = new JPanel();
		inputpanel.setLayout(new BoxLayout(inputpanel, BoxLayout.X_AXIS));
		input = new JTextField(40);
		sendBtn = new JButton("Send");
		inputpanel.add(input);
		inputpanel.add(sendBtn);
		inputpanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 1, 0));
		panel.add(inputpanel);
		// start debug process
		startOpenOCDDaemon();
		startGdbDebugger();
		gdbLoadElfAndConnect();
		
		// frame 
		frame.getContentPane().add(BorderLayout.CENTER,panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setVisible(true);
		// event listener		  
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				if(pocd!=null) pocd.destroy();
				if(pgdb!=null) pgdb.destroy();
			}
		});
		sendBtn.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				gdbwr(input.getText());
				if(cmdIndex<0){
					cmdList.add(input.getText());
				}
				input.setText("");
			}
		});
		  
		input.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				gdbwr(input.getText());
				if(cmdIndex<0){
					cmdList.add(input.getText());
				}
				input.setText("");
			}
		});
		
		KeyListener keyListener = new KeyListener() {

			@Override
			public void keyPressed(KeyEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void keyReleased(KeyEvent evt) {
				if(evt.getKeyCode()==38){
					if(cmdIndex<0){
						cmdIndex = cmdList.size()-1; 
					}else{
						cmdIndex--;
					}
					String cmd = cmdList.get(cmdIndex<0?0:cmdIndex);
					input.setText(cmd);
				}else if(evt.getKeyCode()==40){
					cmdIndex++;
					if(cmdIndex>=cmdList.size()) cmdIndex = cmdList.size()-1;
					String cmd = cmdList.get(cmdIndex<0?0:cmdIndex);
					input.setText(cmd);
				}else{
					cmdIndex=-1;
				}
			}

			@Override
			public void keyTyped(KeyEvent arg0) {
				
			}
			
		};
		input.addKeyListener(keyListener);
		
	}
	
	void startOpenOCDDaemon(){
		try {
			String cmd = Base.getHardwarePath()+"/tools/OpenOCD-0.9.0-dev-arduino/bin/openocd.exe";
			
			ProcessBuilder pb = new ProcessBuilder()
	        .command(cmd, "-s", Base.getHardwarePath()+"/tools/OpenOCD-0.9.0-dev-arduino/share/openocd/scripts" ,"-f",Base.getHardwarePath()+"/arduino/samd/variants/arduino_zero/openocd_scripts/arduino_zero.cfg")
	        .redirectErrorStream(true);
			System.out.println("RUN: "+pb.command());
			
			pocd = pb.start();
			StreamGobbler errorGobbler = new StreamGobbler(pocd.getErrorStream(), "ERROR",false);
			StreamGobbler outputGobbler = new StreamGobbler(pocd.getInputStream(), "OCD",false);

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void startGdbDebugger(){
		try{
			String cmd = Base.getHardwarePath()+"/tools/gcc-arm-none-eabi-4.8.3-2014q1/bin/arm-none-eabi-gdb.exe";
			String folderStr = editor.getSketch().getFolder()+"";
			ProcessBuilder pb = new ProcessBuilder()
			.command(cmd,"-d",folderStr)
			.redirectErrorStream(true);
			System.out.println("RUN: "+pb.command());
			pgdb = pb.start();
			StreamGobbler errorGobbler = new StreamGobbler(pgdb.getErrorStream(), "ERROR",false);
			StreamGobbler outputGobbler = new StreamGobbler(pgdb.getInputStream(), "GDB",true);
			// add stdin for gdb debug command interface
			OutputStream stdin = pgdb.getOutputStream();
			gdbcmd = new BufferedWriter(new OutputStreamWriter(stdin));

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	void gdbwr(String cmd){
		try {
			gdbcmd.write(cmd+"\n");
			gdbcmd.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void gdbLoadElfAndConnect(){
		try {
			String elfPath = ""+elf.toURI();
			editor.statusError("FILE:"+elfPath);
			elfPath = elfPath.replace("file:/", ""); // trim path header
			gdbcmd.write("file "+elfPath+"\n");
			gdbcmd.flush();
			gdbcmd.write("target remote localhost:3333\n");
			gdbcmd.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	
	// http://stackoverflow.com/questions/14165517/processbuilder-forwarding-stdout-and-stderr-of-started-processes-without-blocki
	private class StreamGobbler extends Thread {
	    InputStream is;
	    String type;
	    boolean toConsole;

	    private StreamGobbler(InputStream is, String type, boolean toConsole) {
	        this.is = is;
	        this.type = type;
	        this.toConsole = toConsole;
	    }

	    @Override
	    public void run() {
	        try {
	            InputStreamReader isr = new InputStreamReader(is);
	            BufferedReader br = new BufferedReader(isr);
	            String line = null;
	            while ((line = br.readLine()) != null){
	            	if(!toConsole){
	            		System.out.println(type + "> " + line);
	            	}else{
	            		textArea.append(line+"\n");
	            	}
	            }
	        }
	        catch (IOException ioe) {
	            ioe.printStackTrace();
	        }
	    }
	}

}
