package org.wwscc.dataentry;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;

import org.wwscc.util.IconButton;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.SearchTrigger;

public abstract class MiniInput extends JPanel implements ActionListener
{
	JTextField entry;
	IconButton close;

	/**
	 * Base mini input that set the layout and basic actions (Esc means hide)
	 * @param label the string for the label portion
	 * @param openevent the event to listen for that will cause it to be visible and request focus
	 */
	public MiniInput(String label, MT openevent)
	{
		super(new MigLayout("fill, ins 1", "[60][grow][20]"));
		entry = new JTextField();
		close = new IconButton();
		close.setActionCommand("esc");
		close.addActionListener(this);

		JLabel lbl = new JLabel(label);
		lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12));

		add(lbl, "al right");
		add(entry, "growx");
		add(close, "aligny top");
		setVisible(false);

		Messenger.register(openevent,  new MessageListener() {
			public void event(MT type, Object data) {
				setVisible(true);
				entry.requestFocus();
			}
		});

		entry.registerKeyboardAction(this, "esc", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
		entry.registerKeyboardAction(this, "enter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand() == "esc")
		{
		    entry.setText("");
			setVisible(false);
		}
	}


	/**
	 * Mini input for entering text to find in the current table
	 */
	public static class FilterEntries extends MiniInput
	{
		public FilterEntries()
		{
			super("Filter", MT.OPEN_FILTER);
			entry.getDocument().addDocumentListener(new SearchTrigger() {
				@Override
				public void search(String txt) {
					Messenger.sendEvent(MT.FILTER_ENTRANT, txt);
				}
			});
		}
	}

	/**
	 * Mini input for entering barcodes manually
	 */
	public static class ManualBarcodeInput extends MiniInput
	{
		public ManualBarcodeInput()
		{
			super("Barcode", MT.OPEN_BARCODE_ENTRY);
		}

		public void actionPerformed(ActionEvent e)
		{
			if (e.getActionCommand().equals("enter"))
			{
				Messenger.sendEvent(MT.BARCODE_SCANNED, entry.getText().trim());
				entry.setText("");
			}
			else
				super.actionPerformed(e);
		}
	}
}
