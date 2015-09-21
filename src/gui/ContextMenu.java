package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import modules.BaseModule;
import modules.BaseModule.AvailableModules;
import modules.NRAM;
import modules.Register;
import modules.parts.Port;
import simulator.Main;
import simulator.PickableEntity;
import tools.RotateOperation;
import util.BinData;

public class ContextMenu  {


	public JPopupMenu moduleMenu;
	public JPopupMenu portMenu;
	private List<PickableEntity> entities = new ArrayList<PickableEntity>();
	private Port port;

	private JMenuItem rmLink, rotCW, rotCCW, rot180, copy, paste, delete,
			ramEdit, ramClear, regEdit, regClear;

	/**
	 * Instantiates the menu system, generating the menu items
	 */
	public ContextMenu() {
	    // Remove link
        rmLink = new JMenuItem("Remove Link");
        rmLink.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (port != null && port.link != null) {
					port.link.delete();
				}
            }
        });

        // Rotation action
        ActionListener rotate = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String cmd = event.getActionCommand();
                BaseModule.rotationDir dir;
                if (cmd.contains("right")) {
                    dir = BaseModule.rotationDir.ROT_CW;
                }
                else if (cmd.contains("left")) {
                    dir = BaseModule.rotationDir.ROT_CCW;
                }
                else if (cmd.contains("180")) {
                    dir = BaseModule.rotationDir.ROT_180;
                }
                else {
                    throw new InvalidParameterException(cmd + " is not a valid rotation command.");
                }

                Main.opStack.beginCompoundOp();
                for (PickableEntity e : entities) {
                    if (e.getClass().getGenericSuperclass() == BaseModule.class) {
                        BaseModule m = (BaseModule) e;
                        m.rotate(dir);

                        Main.opStack.pushOp(new RotateOperation(m, dir));
                    }
                }
                Main.opStack.endCompoundOp();
            }
        };

		// Rotate CW
		rotCW = new JMenuItem("Rotate right");
		rotCW.addActionListener(rotate);

		// Rotate CCW
		rotCCW = new JMenuItem("Rotate left");
		rotCCW.addActionListener(rotate);

		// Rotate 180
		rot180 = new JMenuItem("Rotate 180");
		rot180.addActionListener(rotate);

		// Copy/pasteInto
        copy = new JMenuItem("Copy");
        copy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Main.clipboard.copy(entities);
            }
        });

        paste = new JMenuItem("Paste");
        paste.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Main.ui.view.pasteInto();
            }
        });

		// Delete
		delete = new JMenuItem("Delete");
		delete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
                Main.selection.deleteAll();
			}
		});

		////////// Memory-specfic

		// Edit memory
		ramEdit = new JMenuItem("View/Edit NRAM Data");
		ramEdit.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent arg0) {
				for (PickableEntity e : entities) {
					// If it's an NRAM module
					if (e.getType() == PickableEntity.MODULE && ((BaseModule)e).getModType().equals(AvailableModules.RAM)) {
						NRAM nram = (NRAM) e;
						MemEdit editor = Main.ui.newMemEdit();
						editor.show(nram);
					}
				}
		    }
		});

		// Clear memory
		ramClear = new JMenuItem("Clear NRAM Data");
		ramClear.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int res = JOptionPane.showConfirmDialog(Main.ui.frame, "Wiping NRAM data cannot be undone",
						"Are you sure?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

				for (PickableEntity entity : entities) {
					// If it's an NRAM module
					if (entity.getType() == PickableEntity.MODULE &&
							((BaseModule)entity).getModType().equals(AvailableModules.RAM)) {
						NRAM ram = (NRAM) entity;
						switch (res) {
							case JOptionPane.OK_OPTION:
								ram.clear();
								Main.sim.propagate(ram);
							default:
								break;
						}
					}
				}
			}
		});

		////////// Register-specfic

		// Edit
		regEdit = new JMenuItem("Set Register value");
		regEdit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BinData newVal = new BinData(0);
				if (entities.size() == 1) {
					newVal = ((Register)entities.get(0)).getStoredVal();
				}

				String valStr = JOptionPane.showInputDialog(Main.ui.frame, "Enter new Register value:", newVal);
				if (valStr != null) {
					if (valStr.length() != 4) {
                        JOptionPane.showMessageDialog(Main.ui.frame, "4 bits required", "Bad Input",
                                JOptionPane.ERROR_MESSAGE);
						return;
                    }

					boolean b0 = Integer.parseInt(valStr.substring(0, 1)) == 1;
					boolean b1 = Integer.parseInt(valStr.substring(1, 2)) == 1;
					boolean b2 = Integer.parseInt(valStr.substring(2, 3)) == 1;
					boolean b3 = Integer.parseInt(valStr.substring(3)) == 1;
					newVal.setBool(b0, b1, b2, b3);

					for (PickableEntity entity : entities) {
                        if (entity.getType() == PickableEntity.MODULE &&
                                ((BaseModule)entity).getModType().equals(AvailableModules.REGISTER)) {
                            Register reg = (Register) entity;
                            reg.setStoredVal(newVal);

                            Main.sim.propagate(reg);
                        }
                    }
				}
			}
		});

		// Clear
		regClear = new JMenuItem("Clear Register value");
		regClear.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (PickableEntity entity : entities) {
					if (entity.getType() == PickableEntity.MODULE &&
							((BaseModule)entity).getModType().equals(AvailableModules.REGISTER)) {
						Register reg = (Register) entity;
						reg.clear();

						Main.sim.propagate(reg);
					}
				}
			}
		});

	}

	/**
	 * Displays a context-sensitive edit menu
	 * @param modules The 'selection' to operate on
	 * @param x X-position to display the menu at
	 * @param y Y-position to display the menu at
	 */
    public void showEntityMenu(List<PickableEntity> modules, int x, int y) {
	    // Menu to display - fill it in based on context
	    JPopupMenu menu = new JPopupMenu();

	    // Check for a port at the clicked location - for link removal (hacky, yeah)
	    port = ViewUtil.screenSpace_portAt(x, y);

	    // Grab the entities - put them into the class variable
        entities = new ArrayList<PickableEntity>();
        entities.addAll(modules);

	    if (port != null) {
	        menu.add(rmLink);
	    }
	    else {
	        // Standard module options
	        menu.add(rotCW);
	        menu.add(rotCCW);
	        menu.add(rot180);
	        menu.add(copy);
	        menu.add(paste);
	        menu.add(delete);

			// Additional module options
	        for (PickableEntity e : entities) {
    	        // If it's an NRAM module
    	        if (e.getType() == PickableEntity.MODULE && ((BaseModule)e).getModType().equals(AvailableModules.RAM)) {
    	            menu.addSeparator();
    	            menu.add(ramEdit);
					menu.add(ramClear);

					break;
    	        }
	        }

			for (PickableEntity e : entities) {
				// If it's a Register module
				if (e.getType() == PickableEntity.MODULE &&
						((BaseModule)e).getModType().equals(AvailableModules.REGISTER)) {
					menu.addSeparator();
					menu.add(regEdit);
					menu.add(regClear);

					break;
				}
			}
	    }

	    // A menu gets shown any which way
	    menu.show(Main.ui.view, x, y);
	}

}
