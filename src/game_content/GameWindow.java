package game_content;

import java.awt.*;
import resources_classes.ResourceFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.*;

public class GameWindow extends JFrame {

	//Window size
	public static final int windowWidth = 800;
	public static final int windowHeight = 660;
	//Font name
	public static final String fontName = "cootuecursessquare16x16";
	//Number of respawns
	private int respawns = 3;

	public GameWindow() {
		initUI();
	}


	/**
	 * This method initialises the UI of the app.
	 */
	private void initUI() {

		setTitle("Tank War");
		setWindowIcon();
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(null);
		setResizable(false);
		setSize(windowWidth,windowHeight);

		createFont();
		MenuPanel menuPanel = new MenuPanel(this);
		add(menuPanel);

		setLocationRelativeTo(null);
		setVisible(true);

	}

	/**
	 * Minus one respawn
	 */
	public void playerTankDestroyed(){
		respawns--;
	}

	/**
	 * Plus one respawn
	 */
	public void playerRespawnGained(){
		respawns++;
	}

	public int getRespawns(){
		return respawns;
	}

	public void setRespawns(int respawns){
		this.respawns=respawns;
	}


	/**
	 * Add font to windows environment
	 */
	private void createFont(){
		try (InputStream stream = ResourceFile.open("resources/fonts/mainFont.ttf")) {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, stream));
		} catch (Exception e) {
			System.err.println("Font 'resources/fonts/mainFont.ttf' could not be registered (" + e + ")");
		}
	}

	/**
	 * Set window icon
	 */
	private void setWindowIcon(){
		try (InputStream stream = ResourceFile.open("resources/sprites/window/icon.png")) {
			setIconImage(ImageIO.read(stream));
		} catch (IOException e) {
			System.err.println("Window icon could not be loaded (" + e.getMessage() + ")");
		}
	}

	public static void main(String[] args) {

		EventQueue.invokeLater(() -> {
			try {
				//The constructor makes the window visible and loads the menu
				new GameWindow();
			} catch (Throwable t) {
				t.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Tank War could not start:\n" + t,
						"Tank War", JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}
		});
	}
}