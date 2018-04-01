package org.wwscc.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JTextField;

public class HintTextField extends JTextField implements FocusListener
{
    private static final Color mygrey = new Color(180, 180, 180);
    private final String hint;
    private boolean showingHint;

    public HintTextField(final String hint) {
      super(hint);
      this.hint = hint;
      this.showingHint = true;
      super.addFocusListener(this);
      focusLost(null);
    }

    @Override
    public void focusGained(FocusEvent e) {
      if (getText().isEmpty()) {
        setText("");
        setForeground(Color.BLACK);
        setFont(getFont().deriveFont(Font.PLAIN));
        showingHint = false;
      }
    }

    @Override
    public void focusLost(FocusEvent e) {
      if (getText().isEmpty()) {
        setText(hint);
        setForeground(mygrey);
        setFont(getFont().deriveFont(Font.ITALIC));
        showingHint = true;
      }
    }

    @Override
    public String getText() {
      return showingHint ? "" : super.getText();
    }
}
