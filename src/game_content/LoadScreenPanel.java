package game_content;

import javax.swing.*;

import java.awt.*;

import static game_content.GameWindow.*;

public class LoadScreenPanel extends JPanel {

    public LoadScreenPanel(int level){
        this(level, Difficulty.NORMAL);
    }

    public LoadScreenPanel(int level, Difficulty difficulty){
        setLayout(null);
        setBounds(0,0,windowWidth, windowHeight);
        setBackground(Color.DARK_GRAY);
        addLoadText(level, difficulty);

    }

    /**
     * Creates JLabel with text "Stage: number - difficulty"
     * @param level      number of level
     * @param difficulty chosen difficulty
     */
    private void addLoadText(int level, Difficulty difficulty){
        JLabel text = new JLabel("Stage " + level + "  -  " + difficulty);
        text.setFont(new Font(fontName,0,80));
        text.setForeground(Color.WHITE);
        text.setBounds(125,75,800,500);
        text.setOpaque(false);
        add(text);
    }

}
