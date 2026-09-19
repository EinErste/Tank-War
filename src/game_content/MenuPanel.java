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

public class MenuPanel extends JPanel {

    //Music
    private AudioClip music;
    //Keeps the music going, replaced the timer chain that used to leak a timer every 5 seconds
    private Timer musicTimer;
    //Level chooser
    private JComboBox<Level> levelsBox;
    //Difficulty chooser
    private JComboBox<Difficulty> difficultyBox;
    //Background gif
    private JLabel labelBackground;
    //Music boolean
    private boolean musicStop;
    //Parent component
    private GameWindow gameWindow;
    public MenuPanel(GameWindow gameWindow){

        this.gameWindow = gameWindow;
        setBounds(0,0,windowWidth,windowHeight);
        setLayout(null);
        addText();
        addPlayButton();
        addDifficultyComboBox();
        addLevelsComboBox();
        addBackground();
        playMusic();
        checkMusicPlaying();

    }

    /**
     * Creates background gif
     */
    private void addBackground(){

        Image backgroundImage = ScaledImage.create("resources/sprites/menu/background2.gif",windowWidth,windowHeight-30);
        labelBackground = new JLabel(new ImageIcon(backgroundImage));
        labelBackground.setBounds(0, 0, windowWidth, windowHeight - 30);
        add(labelBackground);

    }

    /**
     * Play music
     */
    private void playMusic() {
        music = GameSound.nextMenuMusic();
        music.play();

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
                if (!music.isPlaying() && !musicStop){
                    music = GameSound.nextMenuMusic();
                    music.play();
                }
            }
        });
        musicTimer.start();
    }

    /**
     * Creates JLabel with text "Tank War"
     */
    private void addText(){
        JLabel gameName = new JLabel("<html><div style='text-align: center;'>Tank<br>War</div></html>");
        gameName.setFont(new Font(fontName,1,100));
        gameName.setForeground(new Color(172,17,21));
        gameName.setBounds(200,-100,600,500);
        add(gameName);
    }

    /**
     * Creates play button, which starts the game
     */
    private void addPlayButton(){
        JButton playButton = new JButton("Play");
        playButton.setFont(new Font(fontName,1,50));
        playButton.setForeground(Color.BLACK);
        playButton.setBackground(new Color(172,17,21));
        playButton.setBounds(250,330,300,70);
        playButton.setBorderPainted(false);
        playButton.setVerticalAlignment(SwingConstants.BOTTOM);
        playButton.setFocusPainted(false);
        playButton.addActionListener(e -> {
            stopMusic();
            gameWindow.remove(MenuPanel.this);
            Level level = (Level)levelsBox.getSelectedItem();
            Difficulty difficulty = (Difficulty)difficultyBox.getSelectedItem();
            gameWindow.setRespawns(difficulty.getLives());
            LoadScreenPanel loadScreenPanel = new LoadScreenPanel(level.ordinal()+1, difficulty);

            Timer timer = new Timer(1000, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        gameWindow.remove(loadScreenPanel);
                        GameFieldPanel gameFieldPanel = new GameFieldPanel(gameWindow, level, difficulty);
                        gameWindow.add(gameFieldPanel);
                        gameWindow.revalidate();
                        gameWindow.repaint();
                        gameFieldPanel.requestFocusField();
                    } catch (Throwable t) {
                        //a level that cannot be built would otherwise leave the player on this screen
                        GameWindow.showError("Stage " + (level.ordinal()+1) + " could not be started.", t);
                        gameWindow.showMenu();
                    }
                }
            });
            timer.setRepeats(false);
            timer.start();
            gameWindow.add(loadScreenPanel);
            gameWindow.repaint();
        });
        add(playButton);
    }

    /**
     * Stops the menu music and its keep-alive timer.
     * Without this the timer kept running (and restarting the music) after leaving the menu.
     */
    private void stopMusic(){
        musicStop = true;
        if (musicTimer != null){
            musicTimer.stop();
            musicTimer = null;
        }
        music.stop();
    }

    /**
     * Leaving the window always stops the music, whichever way the menu was left.
     */
    @Override
    public void removeNotify(){
        stopMusic();
        super.removeNotify();
    }

    /**
     * Creates JComboBox which contains the difficulties
     */
    private void addDifficultyComboBox(){
        difficultyBox = new JComboBox<>();
        difficultyBox.setRenderer(new CustomComboBoxCellRenderer<Difficulty>());
        difficultyBox.setFont(new Font(fontName,0,37));
        difficultyBox.setForeground(Color.BLACK);
        difficultyBox.setBackground(new Color(172,17,21));
        difficultyBox.setBounds(250,415,300,70);
        difficultyBox.setToolTipText("Choose difficulty");
        difficultyBox.setMaximumRowCount(3);
        for (Difficulty difficulty : Difficulty.values()) {
            difficultyBox.addItem(difficulty);
        }
        difficultyBox.setSelectedItem(Difficulty.NORMAL);
        add(difficultyBox);
    }

    /**
     * Creates JComboBox which contains levels
     */
    private void addLevelsComboBox(){
        levelsBox = new JComboBox<>();
        levelsBox.setRenderer(new CustomComboBoxCellRenderer<Level>());
        levelsBox.setFont(new Font(fontName,0,37));
        levelsBox.setForeground(Color.BLACK);
        levelsBox.setBackground(new Color(172,17,21));
        levelsBox.setBounds(250,505,300,70);
        levelsBox.setToolTipText("Choose desired level");
        levelsBox.setMaximumRowCount(2);
        for (Level level : Level.values()) {
            levelsBox.addItem(level);
        }
        add(levelsBox);
    }

    //Help class, used for both the difficulty and the level chooser
    class CustomComboBoxCellRenderer<T> extends JLabel implements ListCellRenderer<T> {

        CustomComboBoxCellRenderer(){
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.BOTTOM);
            setFont(new Font(fontName,0,37));
            setForeground(Color.BLACK);
        }

        @Override
        public Dimension getPreferredSize(){
            return new Dimension(300, 60);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends T> list,
                T value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            setText(String.valueOf(value));
            return this;
        }
    }


}
