package de.dietzm.gcodesim;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import de.dietzm.Model;
import de.dietzm.SerialIO;
import de.dietzm.gcodesim.GcodePainter.Commands;
import de.dietzm.print.ConsoleIf;
import de.dietzm.print.Dummy;
import de.dietzm.print.SerialPrinter;


/**
 * Gcode simulator - Free for non-commercial use.
 * 
 * @author mail@dietzm.de
 */

@SuppressWarnings("serial")
public class GcodeSimulator extends Frame implements ActionListener {

	/**
	 * 0.55 Added x/y Offset when gcodes are out of range
	 * 0.6 Added "Loading Model..."  , optimize repaint
	 * 0.65 Added Model details
	 * 0.66 toggle details (model, layer , layer speed , model speed, layer summary) 
	 * 0.67 press (f) to open another file
	 * 0.68 Java 1.6 compatible 
	 * 0.69 Fixed average values (height,temp) , support skeinforge comments , guess diameter, show weight and price
	 * 0.70 Refactored Simulator to abstract AWT implementation (allow other UI implementations like android)
	 * 0.71 Undisruptive zoom (no restart), nextLayer completes the layer painting (not skips it), previous layer cmd added. Printbed grid.
	 * 0.80 Nice looking labels for current infos,  pageing for layer details, about/help dialog.
	 * 		Fixed acceleration (ignore acceleration for speed distribution), use acceleration for paint and layer time. 
	 * 		Smoother Painting by splitting longer lines into multiple 
	 * 0.81 Add mouse listerners , Add default gcode , percent of loading
	 * 0.90 Add Front and Side View, Add experimental printing support (/dev/ttyUSB0 , s-shortcut)
	 * 0.91 Show current speed and remaining print time. Fixed double buffering bug. 
	 * 0.92 Fixed double buffering bug for MAC OS. Show modeldetails by default. Added Menubar 
	 * 0.93 Fixed some multi-threading bugs. Some performance improvements. Icon added. zoom on resize. filedialog path and timer.
	 * 0.94 Fixed temperatur bug , optimize label repaint,
	 * 0.95 Experimental Edit Mode added (Modify menu) 
	 * 0.96 Display Extrusion speed, pause and changed fan output. Fixed grey spots bug.
	 * 0.97 More resilient against errors (Ignore some unknown Gcodes instead of failing)
	 * 0.98 Support for center format G2/G3 Gcodes (Arc) - Radius format not supported yet
	 * 0.99 clicked on speedup label, toggles pause. Replaced blue with a lighter pink to improve readability. Paint nozzle location.
	 * 0.99a Bug: click on speedup label, toggles pause but did also switch layer
	 * 1.01 Added more Gcodes 
	 * 1.02 Added step by step execution (debug mode), Added fast forward/rewind, Show current Gcode when in pause (debug mode), Added increase/decrease in large steps (10x),
	 * 1.03 Fixed large problem with printing (layers were reordered by Z-pos) , better support of z-Lift
	 * 1.04 Network receiver added (Android). confirm on stop printing.  prevent other buttons on print
	 * 1.05+1.06 Android improvements
	 * 1.07 Gcodepainter cleanup, paint GCode when printing, switch background color when printing, network send
	 * 1.08 Mode indicator (print vs simulate) , Framerate counter (disabled)
	 * 1.10 Significantly reduced memory footprint, reworked printing code (decouple render from print), avoid creating many objects
	 * 1.11 fixed boundary calculation, Add makeware specific extrusion code ( A instead of E)
	 * 1.12 GCode creation through factory. Add makeware specific extrusion code ( B instead of E), removed experimental modifications, fixed temp for first layer, fixed comments
	 * 1.13 Fixed network send bug
	 * 1.14 fixed some more bugs. improved load time
	 * 1.15 fixed G4 NPE
	 * 1.16 Improved load error handling. print wrong gcodes in window. fixed double whitespace error
	 * 1.17 Many performance improvments, Paint extruder, MacOS load bug 
	 */
	public static final String VERSION = "v1.17";	
	GcodePainter gp;
	AWTGraphicRenderer awt;
	boolean showdetails =true;


	public GcodeSimulator() {
		setTitle("GCode Print Simulator " + VERSION);
		setBackground(Color.black);
	}
	
	public void init(String filename,InputStream in) throws IOException{
		awt = new AWTGraphicRenderer(GcodePainter.bedsizeX, GcodePainter.bedsizeY,this);
		gp = new GcodePainter(awt);
		updateSize(showdetails);
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource( "/icon.png" )));
		
		setMenuBar(getMyMenubar());
		setVisible(true);
		setBackground(Color.black);
		
		addListeners();

		

		
		gp.start(filename,in);	
	}
	
	
	private void addMenuItem(Menu parent, String name, String key, int keycode){
		MenuItem it = new MenuItem(name);
	    it.addActionListener(this);
	    it.setActionCommand(key);
	    //MenuShortcut ms = new MenuShortcut(KeyEvent.getExtendedKeyCodeForChar(key.charAt(0))); ONLY JAVA 1.7
	    MenuShortcut ms = new MenuShortcut(keycode);
	    it.setShortcut(ms);
	    parent.add (it);
	}
	
	protected MenuBar getMyMenubar () {
		    MenuBar ml = new MenuBar ();
		    Menu datei = new Menu ("File");
		    addMenuItem(datei, "Load File", "f",KeyEvent.VK_F);
		    addMenuItem(datei, "Network Send", "x",KeyEvent.VK_X);
		    addMenuItem(datei, "Exit", "q",KeyEvent.VK_Q);

		    ml.add(datei);		    
		    
		    Menu control = new Menu ("Control");
		    addMenuItem(control, "Pause", "p",KeyEvent.VK_P);
		    control.addSeparator();
		    addMenuItem(control, "Increase Speed", "+",KeyEvent.VK_PLUS);
		    addMenuItem(control, "Increase Speed by 10", "/",KeyEvent.VK_SLASH);
		    addMenuItem(control, "Decrease Speed", "-",KeyEvent.VK_MINUS);
		    addMenuItem(control, "Decrease Speed by 10", "*",KeyEvent.VK_ASTERISK);
		    control.addSeparator();
		    addMenuItem(control, "Next Layer", "n",KeyEvent.VK_N);
		    addMenuItem(control, "Previous Layer", "b",KeyEvent.VK_B);
		    control.addSeparator();
		    addMenuItem(control, "Step Forward", " ",KeyEvent.VK_SPACE);
		    addMenuItem(control, "Step Backward", "\b",KeyEvent.VK_BACK_SPACE);
		    control.addSeparator();
		    addMenuItem(control, "Restart", "r",KeyEvent.VK_R);
		    
		    
		
		    
		    Menu view = new Menu ("View");
		    addMenuItem(view, "Zoom In", "i",KeyEvent.VK_I);
		    addMenuItem(view, "Zoom Out", "o",KeyEvent.VK_O);
		    view.addSeparator();
		    addMenuItem(view, "Show/Hide Details", "m",KeyEvent.VK_M);
		    addMenuItem(view, "Toggle Detail type", "t",KeyEvent.VK_T);
		    		    
		    Menu about = new Menu ("About");
		    addMenuItem(about, "About/Help", "h",KeyEvent.VK_H);
		    		    
		    
		    Menu edit = new Menu ("Modify (Experimental)");
		    edit.add("Experimental Edit Mode");
		    edit.addSeparator();
		    addMenuItem(edit, "Speedup Layer by 10%", "w",KeyEvent.VK_W);
		    addMenuItem(edit, "Slowdown Layer by 10%", "e",KeyEvent.VK_E);
		    addMenuItem(edit, "Increase extrusion by 10%", "z",KeyEvent.VK_Z);
		    addMenuItem(edit, "Decrease extrusion by 10%", "u",KeyEvent.VK_U);
		    addMenuItem(edit, "Delete layer", "g",KeyEvent.VK_G);
		    addMenuItem(edit, "Save Modifications", "a",KeyEvent.VK_A);
		    
		    
		    ml.add(control);
		   // ml.add(edit);
		    ml.add(view);
		    ml.add(about);
		    
		    
		    return ml;
		  }

	public GcodeSimulator getFrame(){
	return this;
	}

	/**
	 * Main class for GCodeSimulator
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		GcodeSimulator gs = new GcodeSimulator();
		String filename;
		InputStream in = null;
		if (args.length < 1 || !new File(args[0]).exists()) {
			filename = "/gcodesim.gcode";
			in= gs.getClass().getResourceAsStream(filename);			
		} else {
			filename = args[0];
		}
		gs.init(filename,in);
		gs.requestFocus();

	}
	
	@Override
	public void actionPerformed(ActionEvent arg0) {
		char a = arg0.getActionCommand().charAt(0);
		//Forward Event to keylistener (don't duplicate code)
		getKeyListeners()[0].keyTyped(new KeyEvent(this, 0, 0, 0, (int)a,a));
	}
	
	public void showNetworkIPDialog(){
		final Dialog in = new Dialog(this,"Send to GCode Simulator for Android",true);
		in.setLayout(new FlowLayout());
		in.setBackground(Color.lightGray);
		final TextField tf2 = new TextField(15);
		final Label status = new Label("                                                    ");
		tf2.setText("192.168.0.50");
//		tf2.setSize(200,20);
		Button btn1 = new Button("Ok");
		
		in.addWindowListener(new WindowListener() {			
			@Override
			public void windowOpened(WindowEvent arg0) {}			
			@Override
			public void windowIconified(WindowEvent arg0) {	}			
			@Override
			public void windowDeiconified(WindowEvent arg0) {}			
			@Override
			public void windowDeactivated(WindowEvent arg0) {}			
			@Override
			public void windowClosing(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowClosed(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowActivated(WindowEvent arg0) {	}
		});
		ActionListener action= new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					NetworkPrinter netp = new NetworkPrinter();
					status.setText("Sending file ... please wait");
					in.repaint();
					netp.sendToReceiver(tf2.getText(), gp.model);
					status.setText("Sending file ... done");
					in.repaint();
					in.setVisible(false);
				}catch (UnknownHostException uh){
					status.setText("Invalid IP address:"+uh.getMessage());
					in.repaint();
					//uh.printStackTrace();
				}catch(ConnectException ce) {
					status.setText("Connect error:"+ce.getMessage());
					in.repaint();
					//ce.printStackTrace();
				}catch (IOException e2) {
					status.setText("Error:"+e2.getMessage());
					in.repaint();
					//e2.printStackTrace();
				}
				
			}
		};
		btn1.addActionListener(action);
		tf2.addActionListener(action);
		in.add(new Label("Enter IP Address"));
		in.add(tf2);
		in.add(btn1);
		in.add(status);
		in.setSize(330,120);
		in.setVisible(true);
	}

	static String openFileBrowser(Frame gs) {
		String filename =null;
//		JFileChooser fc = new JFileChooser();
//		fc.setFileFilter(new FileFilter() {
//			
//			@Override
//			public String getDescription() {
//				return "*.gcode";
//			}
//			
//			@Override
//			public boolean accept(File arg0) {
//				if(arg0.getName().toLowerCase().endsWith(".gcode")) return true;
//				if(arg0.isDirectory()) return true;
//				return false;
//			}
//		});
//		
//		int ret = fc.showOpenDialog(gs);
//		if (ret == JFileChooser.APPROVE_OPTION) {
//			filename = fc.getSelectedFile().getAbsolutePath();
//		}else{
//			System.exit(0);
//		}
		final FileDialog fd = new FileDialog(gs, "Choose a gcode file");
		final Thread gpt = Thread.currentThread();
// 		Native file dialog is better but it has a bug when selecting recent files :-(
		Timer t = new Timer(); 
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				if(fd.isVisible()){					
					gpt.interrupt();
					fd.setVisible(false);
				}
			}
		}, 100000);
		
		if(new File(System.getProperty("user.home")+"/Desktop/3D/MODELS").exists()){
			fd.setDirectory(System.getProperty("user.home")+"/Desktop/3D/MODELS");
		}else{
				fd.setDirectory(System.getProperty("user.dir"));
		}
		
		fd.setModal(true);
		if (System.getProperty("os.name").startsWith("Mac OS X")) {
			//Allow all filename due to a MacoS bug ?
		}else{
			fd.setFilenameFilter(new FilenameFilter() {
			@Override
			public boolean accept(File arg0, String arg1) {
				if(arg1.toLowerCase().endsWith(".gcode")) return true;
				if(arg1.toLowerCase().endsWith(".gc")) return true;
				return false;
			}
		});
		}
		fd.requestFocus();
		fd.setVisible(true);
		// Choosing a "recently used" file will fail because of redhat
		// bugzilla 881425 / jdk 7165729
		if (fd.getFile() == null)	return null;
		filename = fd.getDirectory() + fd.getFile();
		t.cancel();
		return filename;
	}

	private void updateSize(boolean details) {
		if((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0){
			int[] sz = gp.getSize(details);
			setSize(sz[0],sz[1]);
		}
	}
	
	private void addListeners() {
		
		addMouseWheelListener(new MouseWheelListener() {
			
			@Override
			public void mouseWheelMoved(MouseWheelEvent arg0) {
				int mwrot=arg0.getWheelRotation();
				
				if( arg0.isControlDown() || arg0.isAltDown() ) {
				//Zoom
					if (gp.getZoom() > 1 && mwrot < 0)	gp.setZoom((float) gp.getZoom() + mwrot/10f);
					if (gp.getZoom() < 8 && mwrot > 0)	gp.setZoom(gp.getZoom() + mwrot/10f);
					updateSize(showdetails);
				}else{
					//Speedup (only if in left box
					if(arg0.getPoint().x < GcodePainter.bedsizeX * gp.getZoom()+gp.gap ){
						if( mwrot > 0){
							gp.toggleSpeed(false);
						}else{
							gp.toggleSpeed(true);
						}
					}
					
				}
			}
		});
		
		addMouseListener(new MouseListener() {
			
			@Override
			public void mouseReleased(MouseEvent arg0) {
			}
			
			@Override
			public void mousePressed(MouseEvent arg0) {
			}
			
			@Override
			public void mouseExited(MouseEvent arg0) {
			}
			
			@Override
			public void mouseEntered(MouseEvent arg0) {
			}
			
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if(arg0.getButton() == MouseEvent.BUTTON3){
					if(arg0.isAltDown() || arg0.isControlDown()){
						gp.setCmd(Commands.OPENFILE);
					}else{
						showdetails=!showdetails;
						updateSize(showdetails);
					}
				}else if(arg0.getButton() == MouseEvent.BUTTON2){
						gp.showHelp();
				}else{
					int speedboxpos = (int)(((GcodePainter.bedsizeX*gp.getZoom()+gp.gap)/100)*82)+6;
					int speedboxsize=(int)((GcodePainter.bedsizeX*gp.getZoom()+gp.gap)/100)*12;
					int mousex=arg0.getPoint().x;
					//if clicked on speedup label, toggle pause
					if(mousex >= speedboxpos && mousex <= speedboxpos+speedboxsize && arg0.getPoint().y > GcodePainter.bedsizeY*gp.getZoom()+55){
						gp.togglePause();
					}else if(arg0.getPoint().x > GcodePainter.bedsizeX * gp.getZoom()+gp.gap ){
						gp.toggleType();
					}else{
						if(arg0.isAltDown() || arg0.isControlDown()){
							gp.setCmd(Commands.PREVIOUSLAYER);
						}else {
							gp.setCmd(Commands.NEXTLAYER);
						}
					}
				}
				
			}
		});
		// WindowListener
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
			
		}
		
	);
		
		addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(ComponentEvent e) {
				super.componentResized(e);
				float size = getHeight();
				float currsize = gp.getSize(false)[1];
				
				if(currsize!=size){
					//System.out.println("Size:"+size+" Curr:"+currsize);
					//float fac = size/currsize;
					float fac = (size-(55+(size/12)))/GcodePainter.bedsizeY;
					//float z = gp.getZoom();
					//System.out.println("Zoom:"+z);
					//gp.setZoom(z*(fac));
					gp.setZoom((fac));
				}
			}
		
		});
		
		addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent arg0) {
				if (arg0.getKeyChar() == '+') {
					gp.toggleSpeed(true);
				} else if (arg0.getKeyChar() == '-') {
					gp.toggleSpeed(false);
				} else if (arg0.getKeyChar() == '/') {
					gp.toggleSpeed(true,10);
				} else if (arg0.getKeyChar() == '*') {
					gp.toggleSpeed(false,10);
				} else if (arg0.getKeyChar() == ' ') {
					gp.doStep(true);
				} else if (arg0.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
					gp.doStep(false);
				} else if (arg0.getKeyChar() == 'n') { // next layer
					gp.setCmd(Commands.NEXTLAYER);
				} else if (arg0.getKeyChar() == 'b') { // layer before
					gp.setCmd(Commands.PREVIOUSLAYER);
				} else if (arg0.getKeyChar() == 'd') { // debug
					gp.setCmd(Commands.DEBUG);
				} else if (arg0.getKeyChar() == 'm') { // modeldetails
					showdetails=!showdetails;
					updateSize(showdetails);
				} else if (arg0.getKeyChar() == 't') { // detailstype
					gp.toggleType();
				} else if (arg0.getKeyChar() == 'r') { // restart
					gp.setCmd(Commands.RESTART);
				} else if (arg0.getKeyChar() == 'i') { // zoom in
					if (gp.getZoom() < 8)
						gp.setZoom(gp.getZoom() + 0.5f);
						updateSize(showdetails);
					// TODO
					// printstroke = new BasicStroke(zoom - 0.5f,
					// BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					// gp.setCmd(Commands.REPAINTLAYERS);
				} else if (arg0.getKeyChar() == 'o') { // zoom out
					if (gp.getZoom() > 1)
						gp.setZoom((float) gp.getZoom() - 0.5f);
						updateSize(showdetails);

					// TODO
					// printstroke = new BasicStroke(zoom - 0.5f,
					// BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					// gp.setCmd(Commands.RESTART);
				} else if (arg0.getKeyChar() == 'q') {
					System.exit(0);
				} else if (arg0.getKeyChar() == 'f') { // open file
					gp.setCmd(Commands.OPENFILE);
				} else if (arg0.getKeyChar() == 'p') {
					gp.togglePause();
				} else if (arg0.getKeyChar() == 's') {
					try {
						
						ConsoleIf cons=new ConsoleIf() {
							
							@Override
							public void setWakeLock(boolean active) {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public void setTemp(CharSequence temp) {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public void setPrinting(boolean printing) {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public void log(String tag, String value) {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public boolean hasWakeLock() {
								// TODO Auto-generated method stub
								return false;
							}
							
							@Override
							public void clearConsole() {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public int chooseDialog(String[] items, String[] values) {
								// TODO Auto-generated method stub
								return 0;
							}
							
							@Override
							public void appendTextNoCR(CharSequence... txt) {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public void appendText(CharSequence... txt) {
								for (CharSequence n : txt) {
									System.out.println(n);
						         }
																
							}
						};
						SerialPrinter	sio = new SerialPrinter(cons);
						
						
						gp.setPrintercon(sio);
						
						sio.connect(new Dummy(sio, cons),115200);			
						} catch (NoClassDefFoundError er) {
							//er.printStackTrace();
							System.out.println("Opening COM Port FAILED ! RXTX Jar Missing.  " + er);
						} catch (Exception e) {
							e.printStackTrace();
							System.out.println("Opening COM Port FAILED ! " + e);
						} catch (UnsatisfiedLinkError ule){
							//ule.printStackTrace();
							System.out.println("Opening COM Port FAILED ! RXTX Jar Missing.  " + ule);
						}
					gp.togglePrint();
				} else if (arg0.getKeyChar() == 'x') {
					showNetworkIPDialog();
				} else if (arg0.getKeyChar() == 'h') {
					gp.showHelp();
					
				//EDIT MODE
				} else if (arg0.getKeyChar() == 'g') {
					Model.deleteLayer(Collections.singleton(gp.getCurrentLayer()));
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'w') {
					Model.changeSpeed(Collections.singleton(gp.getCurrentLayer()),10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'e') {
					Model.changeSpeed(Collections.singleton(gp.getCurrentLayer()),-10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'z') {
					Model.changeExtrusion(Collections.singleton(gp.getCurrentLayer()),10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'u') {
					Model.changeExtrusion(Collections.singleton(gp.getCurrentLayer()),-10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'a') {
					try {
						gp.model.saveModel(gp.model.getFilename()+"-new");
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					if(arg0.getKeyCode() != 0){ //ignore CTRL modifiers
						gp.showHelp();
					}
				}

			}

		

			@Override
			public void keyReleased(KeyEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void keyPressed(KeyEvent arg0) {
				// TODO Auto-generated method stub

			}
		});
	}

	
	public void paint(Graphics g) {
		//g.drawImage(awt.getImage(), 4, 28, this);
		awt.drawImage(g);
//		//Paint current print point
//		g.fillOval((int)awt.getPos()[0]+4,(int)awt.getPos()[1]+53,4,4);
//		g.setColor(Color.white);
//		g.drawOval((int)awt.getPos()[0]-2,(int)awt.getPos()[1]+47,16,16);
//		g.drawOval((int)awt.getPos()[0]+0,(int)awt.getPos()[1]+49,12,12);
//		g.drawOval((int)awt.getPos()[0]+2,(int)awt.getPos()[1]+51,8,8);
//		g.drawOval((int)awt.getPos()[0]+4,(int)awt.getPos()[1]+53,4,4);
		super.paint(g);
	}
	
	
	/**
	 * Overwritten to avoid "clear" on every paint which causes flashing
	 * 
	 * @param g
	 */
	@Override
	public void update(Graphics g) {
		paint(g);
	}

}
