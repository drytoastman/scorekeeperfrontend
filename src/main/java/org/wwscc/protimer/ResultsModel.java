/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.protimer;

import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;
import org.wwscc.dataentry.Sounds;
import org.wwscc.storage.Run;
import org.wwscc.timercomm.RunServiceInterface;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;


public class ResultsModel extends AbstractTableModel implements MessageListener
{
	private static final Logger log = Logger.getLogger(ResultsModel.class.getCanonicalName());
	
	Vector<DualResult> runs;
	Vector<RunServiceInterface> listeners;
	int nextLeftFinish;
	int nextRightFinish;

	double holdLeftDial;
	double holdRightDial;
	
	public ResultsModel() throws IOException
	{
		super();
		runs = new Vector<DualResult>();
		listeners = new Vector<RunServiceInterface>();

		Messenger.register(MT.TREE, this);

		Messenger.register(MT.DIALIN_LEFT, this);
		Messenger.register(MT.DIALIN_RIGHT, this);

		Messenger.register(MT.REACTION_LEFT, this);
		Messenger.register(MT.REACTION_RIGHT, this);
		Messenger.register(MT.SIXTY_LEFT, this);
		Messenger.register(MT.SIXTY_RIGHT, this);
		Messenger.register(MT.FINISH_LEFT, this);
		Messenger.register(MT.FINISH_RIGHT, this);

		Messenger.register(MT.WIN_LEFT, this);
		Messenger.register(MT.WIN_RIGHT, this);
		Messenger.register(MT.LEAD_LEFT, this);
		Messenger.register(MT.LEAD_RIGHT, this);
		Messenger.register(MT.CHALDIAL_LEFT, this);
		Messenger.register(MT.CHALDIAL_RIGHT, this);
		Messenger.register(MT.CHALWIN_LEFT, this);
		Messenger.register(MT.CHALWIN_RIGHT, this);

		Messenger.register(MT.DELETE_START_LEFT, this);
		Messenger.register(MT.DELETE_START_RIGHT, this);
		Messenger.register(MT.DELETE_FINISH_LEFT, this);
		Messenger.register(MT.DELETE_FINISH_RIGHT, this);

		Messenger.register(MT.INPUT_RESET_SOFT, this);
		Messenger.register(MT.INPUT_RESET_HARD, this);

		nextLeftFinish    = 0;
		nextRightFinish   = 0;
		holdLeftDial	= Double.NaN;
		holdRightDial	= Double.NaN;
	}

	public void addRunServerListener(RunServiceInterface l)
	{
		listeners.add(l);
	}

	@Override
	public int getRowCount()
	{
		return runs.size();
	}

	@Override
	public int getColumnCount()
	{
		return 7;
	}

	@Override
	public String getColumnName(int col)
	{
		switch (col)
		{
			case 0: return "Reaction";
			case 1: return "Sixty";
			case 2: return "Final";
			case 3: return "Message";
			case 4: return "Reaction";
			case 5: return "Sixty";
			case 6: return "Final";
		}

		return "---";
	}


	@Override
	public Class<?> getColumnClass(int col)
	{ 
		if (col == 3) return DualResult.class;
		return ColorTime.class;
	}


	@Override
	public Object getValueAt(int row, int col)
	{
		if (row < runs.size())
		{
			ColorTime c = new ColorTime();
			DualResult dr = runs.get(row);
			switch (col)
			{
				case 0:	c = dr.getLeftReaction(); break;
				case 1:	c = dr.getLeftSixty(); break;
				case 2: c = dr.getLeftFinish(); break;

				case 3: return dr;

				case 4:	c = dr.getRightReaction(); break;
				case 5:	c = dr.getRightSixty(); break;
				case 6: c = dr.getRightFinish(); break;
			}

			/* This will cause a dial to show up under reaction, sixty and final */
			switch (col)
			{
				case 0:
					c.dial = dr.getLeftOrigDial();
					break;
				case 4:
					c.dial = dr.getRightOrigDial();
					break;
			}

			/* Generally return just ColorTime, except for middle which returns entire DualResult */
			return c;
		}	

		return new ColorTime(Double.NaN, 0);
	}


	protected DualResult lastEntry()
	{
		if (runs.isEmpty())
			return null;
		else
			return runs.lastElement();
	}

	protected DualResult getLastFinishLine() throws PSIException
	{
		int index;
		if (nextLeftFinish > nextRightFinish) index = nextLeftFinish; else index = nextRightFinish;
		return runs.get(index-1);
	}

	public int getLastLeftFinish() { return nextLeftFinish - 1; }
	public int getLastRightFinish() { return nextRightFinish - 1; }


	@Override
	public void event(MT type, Object o)
	{
		try 
		{
			Object[] result;
			Double[] doubles;

			switch (type)
			{
				case TREE: createNewEntry(); break;
				case DIALIN_LEFT: holdLeftDial = (Double)o; break;
				case DIALIN_RIGHT: holdRightDial = (Double)o; break;

				case REACTION_LEFT:
					addReaction(true, (ColorTime)o);
					break;
				case REACTION_RIGHT:
					addReaction(false, (ColorTime)o);
					break;

				case SIXTY_LEFT:		
					addSixty(true, (ColorTime)o); 
					break;
				case SIXTY_RIGHT:		
					addSixty(false, (ColorTime)o); 
					break;

				case FINISH_LEFT:
					result = (Object[])o;
					addFinish(true, (ColorTime)result[0], (Double)result[1]);
					break;
				case FINISH_RIGHT:
					result = (Object[])o;
					addFinish(false, (ColorTime)result[0], (Double)result[1]);
					break;

				case WIN_LEFT:			getLastFinishLine().setLeftWin(); break;
				case WIN_RIGHT:			getLastFinishLine().setRightWin(); break;
				case LEAD_LEFT:			getLastFinishLine().setLeftLead(); break;
				case LEAD_RIGHT:		getLastFinishLine().setRightLead(); break;
				case CHALWIN_LEFT:		getLastFinishLine().setLeftChallengeWin(); break;
				case CHALWIN_RIGHT:		getLastFinishLine().setRightChallengeWin(); break;

				case CHALDIAL_LEFT:
					doubles = (Double[])o;
					getLastFinishLine().setLeftChallengeDial(doubles[0], doubles[1]);
					break;
				case CHALDIAL_RIGHT:
					doubles = (Double[])o;
					getLastFinishLine().setRightChallengeDial(doubles[0], doubles[1]); 
					break;


				case DELETE_START_LEFT:	 lastEntry().deleteLeftStart(); break;
				case DELETE_START_RIGHT: lastEntry().deleteRightStart(); break;

				case DELETE_FINISH_LEFT:	deleteFinish(true); break;
				case DELETE_FINISH_RIGHT:	deleteFinish(false); break;

				case INPUT_RESET_SOFT:
				case INPUT_RESET_HARD:
					createNewEntry();
					nextLeftFinish = nextRightFinish = runs.size() - 1;
					break;
			}

			fireTableDataChanged();
		}
		catch (PSIException e)
		{
			log.log(Level.INFO, "PSI error in processing {0}", e);
		}
		catch (NullPointerException npe)
		{
			log.info("null returned in processing");
		}
		catch (ArrayIndexOutOfBoundsException aobe)
		{
			log.log(Level.INFO, "error in processing: " + aobe.getMessage(), aobe);
		}
	}


	public void createNewEntry()
	{
		DualResult dr = lastEntry();
		if ((dr == null) || dr.hasLeftReaction() || dr.hasRightReaction())
		{
			runs.add(new DualResult());
		}
	}

	
	public void addReaction(boolean left, ColorTime c) throws PSIException
	{
		DualResult dr = lastEntry();
		if (left)
		{
			dr.setLeftReaction((ColorTime)c);
			dr.setLeftDial(holdLeftDial);
			holdLeftDial = Double.NaN;
			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.left, 1));
		}
		else
		{
			dr.setRightReaction((ColorTime)c);
			dr.setRightDial(holdRightDial);
			holdRightDial = Double.NaN;
			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.right, 2));
		}
	}


	public void addSixty(boolean left, ColorTime c) throws PSIException
	{
		DualResult dr = lastEntry();
		if (left)
		{
			dr.setLeftSixty((ColorTime)c);
			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.left, 1));
		}
		else
		{
			dr.setRightSixty((ColorTime)c);
			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.right, 2));
		}
	}


	public void deleteFinish(boolean left)
	{
		if (left)
		{
			Result r = runs.get(nextLeftFinish-1).deleteLeftFinish();
			if (r != null)
			{
				for (RunServiceInterface l : listeners)
					l.deleteRun(resultToRun(r, 1));
				nextLeftFinish--;
			}
		}
		else
		{
			Result r = runs.get(nextRightFinish-1).deleteRightFinish();
			if (r != null)
			{
				for (RunServiceInterface l : listeners)
					l.deleteRun(resultToRun(r, 2));
				nextRightFinish--;
			}
		}
	}

	
	public void addFinish(boolean left, ColorTime c, double dial) throws PSIException
	{
		Sounds.playBlocked();
		if (left)
		{
			DualResult dr = runs.get(nextLeftFinish);
			while (!dr.hasLeftReaction())	
			{
				nextLeftFinish++;
				dr = runs.get(nextLeftFinish);
			}

			dr.setLeftFinish(c, dial);
			nextLeftFinish++;			

			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.left, 1));
		}
		else
		{
			DualResult dr = runs.get(nextRightFinish);
			while (!dr.hasRightReaction())	
			{
				nextRightFinish++;
				dr = runs.get(nextRightFinish);
			}
	
			dr.setRightFinish(c, dial);
			nextRightFinish++;			

			for (RunServiceInterface l : listeners)
				l.sendRun(resultToRun(dr.right, 2));
		}
	}

	private Run resultToRun(Result r, int course)
	{
		Run run = new Run(r.finish);
		run.setCourse(course);
		run.setReaction(r.rt);
		run.setSixty(r.sixty);
		switch (r.state)
		{
			case Result.REDLIGHT: run.setStatus("RL"); break;
			case Result.NOTSTAGED: run.setStatus("NS"); break;
		}
		return run;
	}
	
	
	public void clear()
	{
		runs.clear();
		fireTableDataChanged();
		nextLeftFinish    = 0;
		nextRightFinish   = 0;
	}
}

