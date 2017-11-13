package org.wwscc.dialogs;

import javax.swing.JLabel;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;

public class StatusDialog extends BaseDialog<Object>
{
	JProgressBar bar;
	JLabel status;
	
	public StatusDialog()
	{
		super(new MigLayout("fill", "fill", "fill"), true);
		
		bar = new JProgressBar();
		bar.setMinimum(0);
		bar.setMaximum(100);
		status = new JLabel("status here");
		mainPanel.add(status, "wmin 300, wrap");
		mainPanel.add(bar, "");
		
		buttonPanel.remove(ok);
		cancel.setText("Close");
	}
	
	public void setStatus(String s, int val)
	{
		status.setText(s);
		if (val < 0) {
			bar.setIndeterminate(true);
		} else {
			bar.setIndeterminate(false);
			bar.setValue(val);
		}
	}	
}
