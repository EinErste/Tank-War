package game_content;

import javax.swing.*;

import java.awt.*;

import static game_content.GameWindow.*;

public class LoadScreenPanel extends JPanel {

    public LoadScreenPanel(int level){
        setLayout(null);
        setBounds(0,0,windowWidth, windowHeight);
        setBackground(Color.DARK_GRAY);
        addLoadText(level);

    }

    /**
     * Creates JLabel with text "Stage: number"
     * @param level number of level
     */
    private void addLoadText(int level){
        JLabel text = new JLabel("Stage " + level);
        text.setFont(new Font(fontName,0,80));
        text.setForeground(Color.WHITE);
        text.setBounds(125,75,800,500);
        text.setOpaque(false);
        add(text);
    }

}
