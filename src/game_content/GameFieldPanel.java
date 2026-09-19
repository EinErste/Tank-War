package game_content;

import map_tools.Level;
import resources_classes.AudioClip;
import resources_classes.GameSound;
import resources_classes.ScaledImage;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static game_content.GameWindow.*;

public class GameFieldPanel extends JPanel {

    //Music
    private AudioClip music = GameSound.nextBattleMusic();
    //Parent
    private GameWindow gameWindow;
    //Game
    private GameField gameField;
    //Interface
    private JLabel numberOfRespawns;
    private JLabel numberEnemyTanksLabel;
    //Count of enemy tanks destroyed
    private int enemyTanksDestroyed;
    //Current level
    private Level level;
    //Booleans which control music, touched by both the game loop and the Swing thread
    private volatile boolean musicMute;
    private volatile boolean musicStop;
    //Interface mute button
    private JButton muteButton;
    //Keeps the music going, replaced the timer chain that used to leak a timer every 5 seconds
    private Timer musicTimer;
    //True as soon as the level ended, so the lose and the win path can never both open the end panel
    private volatile boolean finished;
    //Icon for mute button
    private Image mutedImage = ScaledImage.create("resources/sprites/menu/buttons_icon/mute_button.png",50,50);
    private Image unmutedImage = ScaledImage.create("resources/sprites/menu/buttons_icon/unmute_button.png",50,50);

    public GameFieldPanel(GameWindow gameWindow, Level level){
        this.gameWindow = gameWindow;
        this.level = level;
        setBounds(0,0,windowWidth,windowHeight);
        setLayout(null);
        setBackground(Color.DARK_GRAY);
        addGameField();
        addMuteButton();
        addExitToMenuButton();
        addLifeIcon();
        addFlagIconAndInfo();
        addEnemyTankLabelAndText();
        checkMusicPlaying();

    }

    /**
     * Controls music playing endless
     */
    private void checkMusicPlaying(){
        if(musicStop){
            return;
        }
        musicTimer = new Timer(5000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!music.isPlaying() && !musicMute && !musicStop){
                    music = GameSound.nextBattleMusic();
                    music.play();
                }
            }
        });
        musicTimer.start();
    }

    /**
     * Creates JLabel with tank icon and number
     */
    private void addEnemyTankLabelAndText(){
        Image heart = ScaledImage.create("resources/sprites/menu/enemy_tank_icon.png",75,75);
        JLabel label = new JLabel(new ImageIcon(heart));
        label.setBounds(720, 0, 75, 75);
        add(label);

        numberEnemyTanksLabel = new JLabel(GameField.ENEMY_COUNT-enemyTanksDestroyed+"x");
        numberEnemyTanksLabel.setFont(new Font(fontName,0,30));
        numberEnemyTanksLabel.setForeground(Color.WHITE);
        numberEnemyTanksLabel.setBounds(625, 0, 100, 100);
        add(numberEnemyTanksLabel);
    }



    /**
     * Creates JLabel with life icon and number
     */
    private void addLifeIcon(){
        Image heart = ScaledImage.create("resources/sprites/menu/heart_icon.png",75,75);
        JLabel label = new JLabel(new ImageIcon(heart));
        label.setBounds(720, 200, 75, 75);
        add(label);

        numberOfRespawns = new JLabel(gameWindow.getRespawns()+"x");
        numberOfRespawns.setFont(new Font(fontName,0,40));
        numberOfRespawns.setForeground(Color.WHITE);
        numberOfRespawns.setBounds(635, 200, 100, 100);
        add(numberOfRespawns);
    }

    /**
     * Creates JLabel with flag icon and number
     */
    private void addFlagIconAndInfo(){
        Image flagImage = ScaledImage.create("resources/sprites/menu/flag_icon.png",100,100);
        JLabel flag = new JLabel(new ImageIcon(flagImage));
        flag.setBounds(710, 380, 100, 100);
        add(flag);
        JLabel number = new JLabel(level.ordinal()+1+"x");
        number.setFont(new Font(fontName,0,32));
        number.setForeground(Color.WHITE);
        number.setBounds(635, 400, 100, 100);
        add(number);
    }


    /**
     * Create game field
     */
    private void addGameField(){
        gameField = new GameField(level, this);
        gameField.setBounds(0,0,624,624);
        add(gameField);
        music.setVolume(GameSound.battleMusicVolume);
        music.play();
    }

    /**
     * Creates interface mute button
     */
    private void addMuteButton(){
        muteButton = new JButton(new ImageIcon(unmutedImage));
        muteButton.setBounds(640,540,50,50);
        muteButton.setBackground(Color.DARK_GRAY);
        muteButton.setBorderPainted(false);
        muteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (musicMute){
                    muteButton.setIcon(new ImageIcon(unmutedImage));
                    music.play();
                    musicMute = false;
                    requestFocusField();
                } else {
                    muteButton.setIcon(new ImageIcon(mutedImage));
                    //Jazz music stops.jpg
                    music.stop();
                    musicMute=true;
                    requestFocusField();
                }
            }
        });
        add(muteButton);
    }

    /**
     * Creates exit to menu button
     */
    private void addExitToMenuButton(){
        JButton exitButton = new JButton(new ImageIcon(ScaledImage.create("resources/sprites/menu/buttons_icon/exit_button.png",50,50)));
        exitButton.setBackground(Color.DARK_GRAY);
        exitButton.setBorderPainted(false);
        exitButton.setBounds(730,540,50,50);
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                finished = true;
                tearDown();
                gameWindow.remove(GameFieldPanel.this);
                gameWindow.setRespawns(3);
                gameWindow.add(new MenuPanel(gameWindow));
                gameWindow.repaint();

            }
        });
        add(exitButton);
    }

    /**
     * Request focus for game field
     */
    public void requestFocusField(){
        gameField.requestFocus();
    }

    /**
     * Method which is called to change level
     */
    public void roundWon(){
        if (finished){
            return;
        }
        if (level.ordinal()+1==Level.values().length){
            gameWon();
            return;
        }
        finished = true;
        tearDown();
        gameWindow.remove(this);
        this.setVisible(false);
        LoadScreenPanel loadScreenPanel = new LoadScreenPanel(level.ordinal()+2);

        Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gameWindow.remove(loadScreenPanel);
                GameFieldPanel gameFieldPanel = new GameFieldPanel(gameWindow, Level.values()[level.ordinal()+1]);
                gameWindow.add(gameFieldPanel);
                gameWindow.repaint();
                gameFieldPanel.requestFocusField();
            }
        });
        timer.setRepeats(false);
        timer.start();
        gameWindow.add(loadScreenPanel);
        gameWindow.repaint();
    }

    /**
     * Method which is called when player looses
     */
    public void gameLost(){
        gameEnd(false);
    }

    /**
     * Method which is called when player wins
     */
    private void gameWon(){
        gameEnd(true);
    }

    /**
     * Main game end method, calls end panel with stats and exot to menu
     * @param gameResult
     */
    private void gameEnd(boolean gameResult){
        if (finished){
            return;
        }
        finished = true;
        tearDown();
        setVisible(false);
        gameWindow.remove(this);
        GameEndPanel gameEndPanel = new GameEndPanel(gameWindow, gameResult, level.ordinal()*GameField.ENEMY_COUNT+enemyTanksDestroyed);
        gameWindow.add(gameEndPanel);
        gameWindow.repaint();

    }

    /**
     * Stops the music, the music keep-alive timer and the level itself.
     * Called before the panel is thrown away, on the Swing thread.
     */
    private void tearDown(){
        musicStop = true;
        if (musicTimer != null){
            musicTimer.stop();
            musicTimer = null;
        }
        music.stop();
        gameField.dispose();
    }

    /**
     * Leaving the window always stops the music, the keep-alive timer and the level, whichever way
     * the panel was left (buttons, level transition, game over or a plain remove).
     */
    @Override
    public void removeNotify(){
        tearDown();
        super.removeNotify();
    }

    /**
     * Minus one respawn and change JLabel
     * <p>
     * Called from the game loop thread, so only the label update is handed over to the Swing thread.
     */
    public void playerTankDestroyed(){
        gameWindow.playerTankDestroyed();
        int respawns = gameWindow.getRespawns();
        if(respawns!=-1){
            SwingUtilities.invokeLater(() -> numberOfRespawns.setText(respawns+"x"));
        }
    }

    /**
     * Count enemy tanks destroyed
     * <p>
     * Called from the game loop thread, so only the label update is handed over to the Swing thread.
     */
    public void enemyTankDestroyed(){
        enemyTanksDestroyed++;
        int tanksLeft = GameField.ENEMY_COUNT-enemyTanksDestroyed;
        SwingUtilities.invokeLater(() -> numberEnemyTanksLabel.setText(tanksLeft+"x"));
        if (enemyTanksDestroyed==GameField.ENEMY_COUNT){
            Timer timer = new Timer(3000, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    roundWon();
                }
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

    public int getRespawns(){
        return gameWindow.getRespawns();
    }

    /**
     * Plus one respawn
     */
    public void playerRespawnGained(){
        gameWindow.playerRespawnGained();
        int respawns = gameWindow.getRespawns();
        SwingUtilities.invokeLater(() -> numberOfRespawns.setText(respawns+"x"));
    }

    /**
     * Stop music
     */
    public void musicStop(){
        musicMute=true;
        music.stop();
        SwingUtilities.invokeLater(() -> {
            muteButton.setIcon(new ImageIcon(mutedImage));
            requestFocusField();
        });
    }

    /**
     * Play music
     */
    public void musicPlay(){
        music.stop();
        music = GameSound.nextBattleMusic();
        musicMute=false;
        if(isVisible()){
            music.play();
        }
        SwingUtilities.invokeLater(() -> {
            muteButton.setIcon(new ImageIcon(unmutedImage));
            requestFocusField();
        });
    }


}
