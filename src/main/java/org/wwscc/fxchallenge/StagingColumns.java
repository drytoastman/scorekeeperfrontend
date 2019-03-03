/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;

import org.wwscc.fxchallenge.StagingCells.CellType;

@SuppressWarnings({ "rawtypes", "unchecked" })  // Generics just makes everything messy in the columns case
public class StagingColumns
{
    static public void setupColumns(List<TableColumn<ChallengePair,?>> columns)
    {
        for (TableColumn<ChallengePair, ?> col : columns) {
            setupColumns(col.getColumns()); // recurse for subcolumns
            String id = col.getId();
            if (id == null) continue;

            switch(id) {
                case "colRound":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().round);
                    break;
                case "colAnnouncer":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().announcer);
                    break;

                case "colDialLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.dial);
                    break;
                case "colNameLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.STRING, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.name);
                    break;
                case "colReactionLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.reaction);
                    break;
                case "colSixtyLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.sixty);
                    break;
                case "colTimeLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.raw);
                    break;
                case "colConesLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.CONESGATES, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.cones);
                    break;
                case "colGatesLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.CONESGATES, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.gates);
                    break;
                case "colStatusLeft":
                    col.setCellFactory(StagingCells.Factory(CellType.STATUS, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.status);
                    break;

                case "colDialRight":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.dial);
                    break;
                case "colNameRight":
                    col.setCellFactory(StagingCells.Factory(CellType.STRING, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.name);
                    break;
                case "colReactionRight":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.reaction);
                    break;
                case "colSixtyRight":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "nextDialCell", ""));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.sixty);
                    break;
                case "colTimeRight":
                    col.setCellFactory(StagingCells.Factory(CellType.TIME, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.raw);
                    break;
                case "colConesRight":
                    col.setCellFactory(StagingCells.Factory(CellType.CONESGATES, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.cones);
                    break;
                case "colGatesRight":
                    col.setCellFactory(StagingCells.Factory(CellType.CONESGATES, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.gates);
                    break;
                case "colStatusRight":
                    col.setCellFactory(StagingCells.Factory(CellType.STATUS, "", "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.status);
                    break;

                default:
                    break;
            }
        }
    }
}
