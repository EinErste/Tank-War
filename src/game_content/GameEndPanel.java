package game_content;

import resources_classes.AudioClip;
import resources_classes.GameSound;
import resources_classes.ScaledImage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static game_content.GameWindow.*;
import static game_content.GameWindow.fontName;
import static resources_classes.ScaledImage.darkRed;

public class GameEndPanel extends JPanel {

    //Music
    private AudioClip music;
    //Parent component
    private GameWindow gameWindow;
    //Count tanks destroyed
    private int tanksDestroyed;

    public GameEndPanel(GameWindow gameWindow, boolean win, int tanksDestroyed){
        this.gameWindow = gameWindow;
        this.tanksDestroyed = tanksDestroyed;
        setLayout(null);
        setBounds(0,0,windowWidth, windowHeight);
        setBackground(Color.DARK_GRAY);
        if(win)
            win();
        else
            defeat();
        addMenuButton();
        addGameStatsText();
        addGameStatsStages();
        addGameStatsLifes();
        addGameStatsTanks();

    }

    /**
     * Creates button to menu
     */
    private void addMenuButton(){
        JButton button = new JButton("Menu");
        button.setFont(new Font(fontName,0,25));
        button.setForeground(Color.WHITE);
        button.setBackground(Color.DARK_GRAY);
        button.setBorderPainted(false);
        button.setBounds(650,550,150,50);
        button.setOpaque(false);
        button.setVerticalAlignment(SwingConstants.BOTTOM);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                music.stop();
                gameWindow.remove(GameEndPanel.this);
                gameWindow.add(new MenuPanel(gameWindow));
                gameWindow.setRespawns(3);
                gameWindow.repaint();
            }
        });
        add(button);
    }

    /**
     * Creates JLabel with text "Stats"
     */
    private void addGameStatsText(){
        JLabel text = new JLabel("Stats");
        text.setFont(new Font(fontName,0,50));
        text.setForeground(Color.WHITE);
        text.setBounds(300,75,500,300);
        text.setOpaque(false);
        add(text);
    }

    /**
     * Creates JLabel with text "Stages completed: number of stages" and stage icon
     */
    private void addGameStatsStages(){
        JLabel textTank = new JLabel("<html><font color=white>Stages completed: </font><font color=red>" + tanksDestroyed/GameField.ENEMY_COUNT +"</font></html>");
        textTank.setFont(new Font(fontName,0,30));
        textTank.setForeground(Color.WHITE);
        textTank.setBounds(100,275,800,100);
        textTank.setOpaque(false);
        add(textTank);

        JLabel iconTank = new JLabel(new ImageIcon(ScaledImage.create("resources/sprites/menu/flag_icon.png",50,50)));
        iconTank.setBounds(700,292,50,50);
        iconTank.setOpaque(false);
        add(iconTank);
    }

    /**
     * Creates JLabel with text "Tanks destroyed: number of tanks " and tank icon
     */
    private void addGameStatsTanks(){
        JLabel textTank = new JLabel("<html><font color=white>Tanks destroyed: </font><font color=red>" + tanksDestroyed +"</font></html>");
        textTank.setFont(new Font(fontName,0,30));
        textTank.setForeground(Color.WHITE);
        textTank.setBounds(100,350,800,100);
        textTank.setOpaque(false);
        add(textTank);

        JLabel iconTank = new JLabel(new ImageIcon(ScaledImage.create("resources/sprites/menu/enemy_tank_icon.png",50,50)));
        iconTank.setBounds(700,367,50,50);
        iconTank.setOpaque(false);
        add(iconTank);
    }

    /**
     * Creates JLabel with text "Lifes left: number of lifes" and life icon
     */
    private void addGameStatsLifes(){
        int respawns = (gameWindow.getRespawns()==-1)? gameWindow.getRespawns()+1: gameWindow.getRespawns();
        JLabel textLife = new JLabel("<html><font color=white>Lifes left: </font><font color=red>" + respawns +"</font></html>");
        textLife.setFont(new Font(fontName,0,30));
        textLife.setForeground(Color.WHITE);
        textLife.setBounds(200,425,800,100);
        textLife.setOpaque(false);
        add(textLife);

        JLabel iconTank = new JLabel(new ImageIcon(ScaledImage.create("resources/sprites/menu/heart_icon.png",50,50)));
        iconTank.setBounds(700,442,50,50);
        iconTank.setOpaque(false);
        add(iconTank);
    }

    /**
     * Creates win screen with music
     */
    private void win(){
        music = GameSound.getWinMusicInstance();
        music.play();
        JLabel text = new JLabel("WIN");
        text.setFont(new Font(fontName,0,80));
        text.setForeground(darkRed);
        text.setBounds(300,-125,800,500);
        text.setOpaque(false);
        add(text);
    }

    /**
     * Creates defeat screen with music
     */
    private void defeat(){
        music = GameSound.getDefeatMusicInstance();
        music.play();
        JLabel text = new JLabel("DEFEAT");
        text.setFont(new Font(fontName,0,80));
        text.setForeground(darkRed);
        text.setBounds(175,-125,800,500);
        text.setOpaque(false);
        add(text);
    }

}
