/*******************************************************************************
 * Copyright (c) 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - Jay Jay Billings,
 *   Jordan H. Deyton, Dasha Gorin, Alexander J. McCaskey, Taylor Patterson,
 *   Claire Saunders, Matthew Wang, Anna Wojtowicz
 *******************************************************************************/
package org.eclipse.ice.client.widgets.moose;

import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.ice.client.common.ActionTree;
import org.eclipse.ice.client.widgets.ICEDataComponentSectionPart;
import org.eclipse.ice.client.widgets.ICEFormEditor;
import org.eclipse.ice.client.widgets.ICEFormInput;
import org.eclipse.ice.client.widgets.ICEFormPage;
import org.eclipse.ice.client.widgets.ICESectionPage;
import org.eclipse.ice.client.widgets.jme.ViewFactory;
import org.eclipse.ice.client.widgets.moose.components.PlantBlockManager;
import org.eclipse.ice.client.widgets.reactoreditor.plant.PlantAppState;
import org.eclipse.ice.datastructures.form.DataComponent;
import org.eclipse.ice.datastructures.form.Entry;
import org.eclipse.ice.datastructures.form.Form;
import org.eclipse.ice.datastructures.form.TreeComposite;
import org.eclipse.ice.item.nuclear.MOOSEModel;
import org.eclipse.ice.reactor.plant.PlantComposite;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.jme3.math.Vector3f;

/**
 * This class extends the default {@link ICEFormEditor} to enable it to draw a
 * {@link PlantApplication}, a 3D view built on jME3 for viewing plant models.
 * 
 * @author Jordan H. Deyton
 * 
 */
public class MOOSEFormEditor extends ICEFormEditor {

	/**
	 * ID for Eclipse, used for the bundle's editor extension point.
	 */
	public static final String ID = "org.eclipse.ice.client.widgets.moose.MOOSEFormEditor";

	/**
	 * The Eclipse IFormPage ID used for the Plant View page.
	 */
	private static final String PLANT_PAGE_ID = "Plant";
	/**
	 * The Eclipse IFormPage ID used for the Mesh View page.
	 */
	private static final String MESH_PAGE_ID = "Mesh";

	/**
	 * The PlantAppState rendered on the Plant View page.
	 */
	private PlantAppState plantView;

	/**
	 * The factory responsible for synchronizing the current "Components"
	 * TreeComposite with the {@link #plantApplication}'s current
	 * {@link PlantComposite}.
	 */
	private final PlantBlockManager factory = new PlantBlockManager();

	/**
	 * Whether or not to render the plant view with wireframes.
	 */
	private boolean wireframe;

	/**
	 * Overrides the default <code>ICEFormEditor</code> header and adds the
	 * widgets for specifiying the output for the MOOSE model (i.e., the input
	 * file for the MOOSE-based simulation).
	 */
	@Override
	protected void createHeaderContents(IManagedForm headerForm) {

		// Get the Form that provides the common header and decorate it.
		org.eclipse.ui.forms.widgets.Form form = headerForm.getForm().getForm();
		FormToolkit formToolkit = headerForm.getToolkit();
		formToolkit.decorateFormHeading(form);

		// Create a composite for the overall head layout.
		Composite headClient = new Composite(form.getHead(), SWT.NONE);

		// Set the layout to a GridLayout. It will contain separate columns for
		// the description and, if applicable, process widgets (a label, a
		// dropdown, and go/cancel buttons).
		GridLayout gridLayout = new GridLayout(1, false);
		headClient.setLayout(gridLayout);

		// Create a label to take up the first space and provide the
		// description of the Form.
		Label descLabel = new Label(headClient, SWT.WRAP);
		descLabel.setText(iceDataForm.getDescription());

		// Create the GridData for the label. It must take up all of the
		// available horizontal space, but capable of shrinking down to the
		// minimum width.
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		// For the minimum width, pick a length based on the average character
		// width with the label's font. Use, say, 35 characters.
		GC gc = new GC(descLabel);
		int widthOf50Chars = gc.getFontMetrics().getAverageCharWidth() * 35;
		gc.dispose();
		// We set the min width so the label won't shrink below that width. We
		// set the width hint to the same value so the widget won't compute its
		// size base on SWT.DEFAULT (if this is the case, it won't wrap).
		gridData.minimumWidth = widthOf50Chars;
		gridData.widthHint = widthOf50Chars;
		descLabel.setLayoutData(gridData);

		// Create the process label, button and dropdown if the action list is
		// available
		if (iceDataForm.getActionList() != null) {

			// Create the output file label
			Label outputLabel = new Label(headClient, SWT.NONE);
			outputLabel.setText("Output File Name:");

			// Create the output file text box
			final Text outputFileText = new Text(headClient, SWT.LEFT
					| SWT.BORDER);
			ICEFormInput formInput = (ICEFormInput) getEditorInput();
			Form dataForm = formInput.getForm();
			DataComponent dataComp = (DataComponent) dataForm
					.getComponent(MOOSEModel.fileDataComponentId);
			final Entry entry = dataComp.retrieveEntry("Output File Name");
			outputFileText.setToolTipText(entry.getDescription());
			outputFileText.setText(entry.getValue());
			// Adapt the text's visual appearance to Form defaults.
			formToolkit.adapt(outputFileText, true, false);
			// Add the Focus Listeners
			outputFileText.addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					// Set the value of the Entry
					entry.setValue(outputFileText.getText());
					// Notify the listeners that a change may have occurred.
					notifyUpdateListeners();
				};
			});

			// Create a label for the process buttons
			Label processLabel = new Label(headClient, SWT.NONE);
			processLabel.setText("Process:");

			// Create the dropdown menu
			processDropDown = new Combo(headClient, SWT.DROP_DOWN | SWT.SINGLE
					| SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
			for (String i : iceDataForm.getActionList()) {
				processDropDown.add(i);
			}
			// Set the default process
			processName = iceDataForm.getActionList().get(0);
			processDropDown.select(0);
			// Add the dropdown listener
			processDropDown.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					// Set the action value to use when processing
					processName = processDropDown.getItem(processDropDown
							.getSelectionIndex());
				}
			});

			// Create the button to process the Form
			goButton = new Button(headClient, SWT.PUSH);
			goButton.setText("Go!");

			// Set the button's listener and process command
			goButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					// Process the Form
					notifyProcessListeners(processName);
				}
			});

			// Create the button to cancel the process
			cancelButton = new Button(headClient, SWT.PUSH);
			cancelButton.setText("Cancel");

			// Set the button's listener and process command
			cancelButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					// Process the Form
					notifyCancelListeners(processName);
				}
			});

			// Since we have more widgets, add more columns to the GridLayout.
			// All of these new widgets should grab what horizontal space they
			// need but be vertically centered.
			gridLayout.numColumns += 6;
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			outputLabel.setLayoutData(gridData);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			outputFileText.setLayoutData(gridData);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			processLabel.setLayoutData(gridData);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			processDropDown.setLayoutData(gridData);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			goButton.setLayoutData(gridData);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, true);
			cancelButton.setLayoutData(gridData);
		}
		// Set the processComposite as the Form's head client
		form.setHeadClient(headClient);

		// Set Form name
		form.setText(iceDataForm.getName() + " " + iceDataForm.getId());

		return;
	}

	/**
	 * Override the method in {@link ICEFormEditor} to prevent the DataComponent
	 * page from being rendered since this class has moved the output file text
	 * to the header and the app selection to the
	 * {@link MOOSETreeCompositeViewer}.
	 */
	@Override
	protected ArrayList<ICEFormPage> createDataTableAndMatrixComponentPages() {

		// Add an empty page for future additions to the MOOSE Model Builder.
		ArrayList<ICEFormPage> sectionPages = new ArrayList<ICEFormPage>();
		sectionPages.add(new ICESectionPage(this, "MOOSE Page", "MOOSE Page"));
		return sectionPages;
	}

	/**
	 * Provides a Plant View page with a single {@link PlantApplication} for use
	 * with RELAP-7.
	 */
	public void addPlantPage() {
		// Do not add more than one plant page.
		if (findPage(PLANT_PAGE_ID) == null) {

			// Add a page with a plant view.
			try {
				addPage(new ICEFormPage(this, PLANT_PAGE_ID, "Plant View") {
					@Override
					protected void createFormContent(IManagedForm managedForm) {

						// The plant view should consume the whole page.
						Section section;
						FormToolkit toolkit = managedForm.getToolkit();

						// Set up the overall layout (FillLayout).
						Composite body = managedForm.getForm().getBody();
						body.setLayout(new FillLayout());

						// Create a Section for the plant view.
						section = toolkit.createSection(body, Section.NO_TITLE
								| Section.EXPANDED);
						populatePlantViewSection(section, toolkit);
						// No layout data to set for FillLayouts.

						return;
					}
				});
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}

		return;
	}

	/**
	 * Creates the content used for the plant view.
	 * 
	 * @param section
	 *            The {@code Section} that should contain the plant view.
	 * @param toolkit
	 *            The {@code FormToolkit} used to decorate widgets as necessary.
	 */
	private void populatePlantViewSection(Section section, FormToolkit toolkit) {
		// Get the background color to use later.
		Color background = section.getBackground();

		// Create an analysis composite to contain a ToolBar and an
		// analysis-based view.
		Composite analysisComposite = new Composite(section, SWT.NONE);
		analysisComposite.setBackground(background);
		analysisComposite.setLayout(new GridLayout(1, false));
		// Set the overall client of the plant view's Section.
		section.setClient(analysisComposite);

		// Create a ToolBarManager so we can add JFace Actions to it.
		ToolBarManager toolBarManager = new ToolBarManager(SWT.RIGHT);
		// Fill the ToolBar with customized controls.
		fillPlantViewToolBar(toolBarManager);
		toolBarManager.update(true);
		// Add it to the view.
		ToolBar toolBar = toolBarManager.createControl(analysisComposite);
		toolBar.setBackground(background);
		toolBar.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		// Create the plant view.
		TreeComposite components = findComponentBlock();
		factory.setTree(components);
		PlantComposite plant = factory.getPlant();
		plantView = new ViewFactory().createPlantView(plant);

		// Render the plant view in the analysis Composite.
		Composite plantComposite = plantView.createComposite(analysisComposite);
		plantComposite.setBackground(background);
		plantComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true,
				true));

		// Make sure the factory/plant is reset when the plant view is disposed.
		plantComposite.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				factory.setTree(new TreeComposite());
			}
		});

		return;
	}

	/**
	 * Fills the plant view's {@code ToolBar} with supported actions.
	 * 
	 * @param toolBar
	 *            The plant view's {@code ToolBar}'s manager.
	 */
	private void fillPlantViewToolBar(ToolBarManager toolBar) {
		Action action;

		// Set the action's image (a camera).
		Bundle bundle = FrameworkUtil.getBundle(getClass());
		Path imagePath;
		URL imageURL;
		ImageDescriptor image;

		// TODO Use an ImageRegistry instead of hard-coded ImageDescriptors.

		final float moveRate = 1f;
		final float rotateRate = (float) (Math.PI * 0.1);

		// Add an action that toggles the wireframe boolean.
		// Also clear the wireframe setting.
		wireframe = false;
		toolBar.add(new Action("Wireframe") {
			@Override
			public void run() {
				wireframe = !wireframe;
				plantView.setWireframe(wireframe);
			}
		});

		// Add a new menu with the following options:
		// Reset the camera - resets the camera's orientation
		// YZ - sets the camera to view the YZ plane
		// XY - sets the camera to view the XY plane
		// ZX - sets the camera to view the ZX plane
		ActionTree cameraTree = new ActionTree("Camera Orientation");
		cameraTree.add(new ActionTree(new Action("Reset to current default") {
			@Override
			public void run() {
				plantView.resetCamera();
			}
		}));
		cameraTree.add(new ActionTree(new Action(
				"YZ (Y right, Z up - initial default)") {
			@Override
			public void run() {
				Vector3f position = new Vector3f(10f, 0f, 0f);
				Vector3f dir = new Vector3f(-1f, 0f, 0f);
				Vector3f up = Vector3f.UNIT_Z;
				plantView.setDefaultCameraPosition(position);
				plantView.setDefaultCameraOrientation(dir, up);
				plantView.resetCamera();
			}
		}));
		cameraTree.add(new ActionTree(new Action("XY (X right, Y up)") {
			@Override
			public void run() {
				Vector3f position = new Vector3f(0f, 0f, 10f);
				Vector3f dir = new Vector3f(0f, 0f, -1f);
				Vector3f up = Vector3f.UNIT_Y;
				plantView.setDefaultCameraPosition(position);
				plantView.setDefaultCameraOrientation(dir, up);
				plantView.resetCamera();
			}
		}));
		cameraTree.add(new ActionTree(new Action("ZX (Z right, X up)") {
			@Override
			public void run() {
				Vector3f position = new Vector3f(0f, 10f, 0f);
				Vector3f dir = new Vector3f(0f, -1f, 0f);
				Vector3f up = Vector3f.UNIT_X;
				plantView.setDefaultCameraPosition(position);
				plantView.setDefaultCameraOrientation(dir, up);
				plantView.resetCamera();
			}
		}));
		toolBar.add(cameraTree.getContributionItem());

		// TODO Move this elsewhere in the ToolBar.
		action = new Action("Save Image") {
			@Override
			public void run() {
				plantView.exportImage();
			}
		};
		// Set the action's image (a camera).
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "camera.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		ImageDescriptor imageDescriptor = ImageDescriptor
				.createFromURL(imageURL);
		action.setImageDescriptor(imageDescriptor);
		ActionTree saveImageTree = new ActionTree(action);
		toolBar.add(saveImageTree.getContributionItem());

		toolBar.add(new Separator());

		// ---- Movement Arrow Buttons ---- //
		// Strafe left
		action = new Action("Move left (A)") {
			@Override
			public void run() {
				plantView.getFlightCamera().strafeCamera(-1f);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-left-perspective-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Move forward
		action = new Action("Move forward (W)") {
			@Override
			public void run() {
				plantView.getFlightCamera().thrustCamera(moveRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-up-perspective-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Move backward
		action = new Action("Move backward (S)") {
			@Override
			public void run() {
				plantView.getFlightCamera().thrustCamera(-moveRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-down-perspective-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Strafe right
		action = new Action("Move right (D)") {
			@Override
			public void run() {
				plantView.getFlightCamera().strafeCamera(moveRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-right-perspective-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Move up
		action = new Action("Move up (SPACE)") {
			@Override
			public void run() {
				plantView.getFlightCamera().raiseCamera(moveRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-up-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Move down
		action = new Action("Move down (C)") {
			@Override
			public void run() {
				plantView.getFlightCamera().raiseCamera(-moveRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-down-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);
		// -------------------------------- //

		toolBar.add(new Separator());

		// ---- Rotation Arrow Buttons ---- //
		// Roll left
		action = new Action("Roll Left (Q)") {
			@Override
			public void run() {
				plantView.getFlightCamera().rollCamera(-rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-roll-left-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Roll right
		action = new Action("Roll Right (E)") {
			@Override
			public void run() {
				plantView.getFlightCamera().rollCamera(rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-roll-right-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Pitch up
		action = new Action("Pitch Up (up arrow)") {
			@Override
			public void run() {
				plantView.getFlightCamera().pitchCamera(rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-pitch-up-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);
		// Pitch down
		action = new Action("Pitch down (down arrow)") {
			@Override
			public void run() {
				plantView.getFlightCamera().pitchCamera(-rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-pitch-down-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);

		// Yaw left
		action = new Action("Yaw Left (left arrow)") {
			@Override
			public void run() {
				plantView.getFlightCamera().yawCamera(-rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-yaw-left-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);
		// Yaw right
		action = new Action("Yaw Right (right arrow)") {
			@Override
			public void run() {
				plantView.getFlightCamera().yawCamera(rotateRate);
			}
		};
		imagePath = new Path("icons" + System.getProperty("file.separator")
				+ "arrow-yaw-right-16.png");
		imageURL = FileLocator.find(bundle, imagePath, null);
		image = ImageDescriptor.createFromURL(imageURL);
		action.setImageDescriptor(image);
		toolBar.add(action);
		// -------------------------------- //

		// ---- Zoom Buttons ---- //
		// ---------------------- //

		return;
	}

	/**
	 * Removes the Plant View page if possible.
	 */
	public void removePlantPage() {
		removePageWithID(PLANT_PAGE_ID);
	}

	/**
	 * Provides a Mesh View page with a view of the MOOSE data tree's mesh
	 * rendered by the current applicable visualization service.
	 */
	public void addMeshPage() {
		// Do not add more than one mesh page.
		if (findPage(MESH_PAGE_ID) == null) {

			// Add a page with a plant view.
			try {
				addPage(new ICEFormPage(this, MESH_PAGE_ID, "Mesh View") {
					@Override
					protected void createFormContent(IManagedForm managedForm) {

						// On the left should be a DataComponentComposite for
						// the "Mesh" block's active data node. On the right
						// should be a view of the mesh, if applicable.
						Section section;
						FormToolkit toolkit = managedForm.getToolkit();

						// Set up the overall layout. Use a GridLayout to get
						// the horizontal layout of the DataComponent and mesh.
						Composite body = managedForm.getForm().getBody();
						body.setLayout(new GridLayout(2, false));

						// Create a Section for the "Mesh" block's active data
						// node (DataComponent).
						section = createDefaultSection(managedForm);
						// The data node should not get excess horizontal space.
						section.setLayoutData(new GridData(SWT.FILL, SWT.FILL,
								false, true));
						populateMeshDataComponentSection(section, toolkit,
								managedForm);

						// Create a Section for the mesh view.
						section = createDefaultSection(managedForm);
						populateMeshViewSection(section, toolkit);
						// The mesh view should grab all excess space.
						section.setLayoutData(new GridData(SWT.FILL, SWT.FILL,
								true, true));

						return;
					}
				});
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}

		return;
	}

	/**
	 * Creates a default, titled, collapsible {@code Section} inside the managed
	 * form.
	 * 
	 * @param managedForm
	 *            The container for the new {@code Section}.
	 * @return The new {@code Section}.
	 */
	private Section createDefaultSection(final IManagedForm managedForm) {
		Composite parent = managedForm.getForm().getBody();
		FormToolkit toolkit = managedForm.getToolkit();
		Section section = toolkit.createSection(parent, Section.DESCRIPTION
				| Section.TITLE_BAR | Section.TWISTIE | Section.EXPANDED);
		section.addExpansionListener(new ExpansionAdapter() {
			public void expansionStateChanged(ExpansionEvent e) {
				managedForm.reflow(true);
			}
		});
		return section;
	}

	/**
	 * Creates the content used for the "Mesh" block's active data node (a
	 * {@code DataComponent}).
	 * 
	 * @param section
	 *            The {@code Section} that should contain the active data node.
	 * @param toolkit
	 *            The {@code FormToolkit} used to decorate widgets as necessary.
	 * @param managedForm
	 *            The managed form. This is required to create a
	 *            {@link ICEDataComponentSectionPart}.
	 */
	private void populateMeshDataComponentSection(Section section,
			FormToolkit toolkit, IManagedForm managedForm) {

		// Find the "file" Entry among the "Mesh" block's parameters.
		TreeComposite meshBlock = findMeshBlock();
		DataComponent activeNode = (DataComponent) meshBlock
				.getActiveDataNode();

		// Create the content for the DataComponent's Section.
		ICEDataComponentSectionPart activeNodeSectionPart = new ICEDataComponentSectionPart(
				section, this, managedForm);
		// Send the active data node to the DataComponentSectionPart.
		activeNodeSectionPart.setDataComponent(activeNode);
		activeNodeSectionPart.renderSection();

		return;
	}

	/**
	 * Creates the content used for the mesh view.
	 * 
	 * @param section
	 *            The {@code Section} that should contain the mesh view.
	 * @param toolkit
	 *            The {@code FormToolkit} used to decorate widgets as necessary.
	 */
	private void populateMeshViewSection(Section section, FormToolkit toolkit) {
		section.setText("Mesh");
		section.setDescription("The current mesh configured for MOOSE input.");

		Composite sectionClient = toolkit.createComposite(section);
		sectionClient.setLayout(new FillLayout());
		section.setClient(sectionClient);
		sectionClient.setBackground(Display.getCurrent().getSystemColor(
				SWT.COLOR_BLACK));
	}

	// /**
	// * Creates the content used for the mesh view.
	// *
	// * @param formToolkit
	// * The toolkit used to make SWT components appear standardized,
	// * if desired.
	// * @param parent
	// * The parent (intended to be the parent {@code Section} in the
	// * mesh view page).
	// * @param managedForm
	// * The form hosted in this page.
	// * @return The top-level {@code Composite} required for the plant view.
	// */
	// private Composite createMeshPage(FormToolkit formToolkit, Composite
	// parent,
	// final IManagedForm managedForm) {
	//
	// // We want to create two sections: one to hold the "Mesh" block's
	// // DataComponent, and one to hold the rendered mesh.
	// Composite sectionClient = formToolkit.createComposite(parent, SWT.NONE);
	// sectionClient.setLayout(new GridLayout(2, false));
	// int sectionStyle = Section.DESCRIPTION | Section.TITLE_BAR;
	//
	// // ---- DataComponent Section ---- //
	// // Create a decorated Section to contain the "Mesh" block's active data
	// // node (a DataComponent).
	// Section activeNodeSection = formToolkit.createSection(sectionClient,
	// sectionStyle);
	// activeNodeSection.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false,
	// true));
	// // Create the content for the DataComponent's Section.
	// ICEDataComponentSectionPart activeNodeSectionPart = new
	// ICEDataComponentSectionPart(
	// (Section) parent, this, managedForm);
	// // Send the active data node to the DataComponentSectionPart.
	// activeNodeSectionPart.setDataComponent(activeNode);
	// activeNodeSectionPart.renderSection();
	// // ------------------------------- //
	//
	// // ---- Mesh Section ---- //
	// // Create a decorated Section to contain the rendered mesh.
	// Section meshSection = formToolkit.createSection(sectionClient,
	// sectionStyle);
	// meshSection.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	// // Create the content for the mesh's Section.
	// MeshViewComposite meshSectionClient = new MeshViewComposite(parent,
	// SWT.NONE);
	// formToolkit.adapt(meshSectionClient);
	// // Send the Entry (if possible) to the mesh view for processing.
	// if (activeNode != null) {
	// meshSectionClient.setFileEntry(activeNode.retrieveEntry("file"));
	// }
	// // ---------------------- //
	//
	// return sectionClient;
	// }

	/**
	 * Removes the Mesh View page if possible.
	 */
	public void removeMeshPage() {
		removePageWithID(MESH_PAGE_ID);
	}

	/**
	 * Removes the page with the specified ID.
	 * 
	 * @param id
	 *            The ID of the page, e.g. {@link #PLANT_PAGE_ID} or
	 *            {@link #MESH_PAGE_ID}.
	 */
	private void removePageWithID(String id) {
		IFormPage page = findPage(id);
		if (page != null) {
			removePage(page.getIndex());
		}
	}

	/**
	 * Finds the "Components" block in the MOOSE tree.
	 * 
	 * @return The "Components" block, or an empty, default tree if one could
	 *         not be found.
	 */
	private TreeComposite findComponentBlock() {
		// This is a convenience method. Currently, it is very similar to
		// findMeshBlock(), so the code has been relocated and shared in
		// findNamedRootBlock(), although this may change soon.
		return findNamedRootBlock("Components");
	}

	/**
	 * Finds the "Mesh" block in the MOOSE tree.
	 * 
	 * @return The "Mesh" block, or an empty, default tree if one could not be
	 *         found.
	 */
	private TreeComposite findMeshBlock() {
		// This is a convenience method. Currently, it is very similar to
		// findComponentBlock(), so the code has been relocated and shared in
		// findNamedRootBlock(), although this may change soon.
		return findNamedRootBlock("Mesh");
	}

	/**
	 * Finds a block with the specified name under the top level of the MOOSE
	 * data tree.
	 * 
	 * @param name
	 *            The name of the block to find. This is not checked for null
	 *            since this is a private method.
	 * @return The first block ({@code TreeComposite}) with a matching name, or
	 *         a default, empty TreeComposite.
	 */
	private TreeComposite findNamedRootBlock(String name) {
		TreeComposite namedRootBlock = null;

		// Get the root TreeComposite from the form.
		TreeComposite root = (TreeComposite) iceDataForm
				.getComponent(MOOSEModel.mooseTreeCompositeId);

		// Find the "Mesh" TreeComposite. We will need to pull the mesh from
		// this node as a file resource.
		for (int i = 0; i < root.getNumberOfChildren(); i++) {
			TreeComposite child = root.getChildAtIndex(i);
			if (name.equals(child.getName())) {
				namedRootBlock = child;
				// Break from the loop.
				i = root.getNumberOfChildren();
			}
		}

		return (namedRootBlock != null ? namedRootBlock : new TreeComposite());
	}

	/**
	 * Tells the FormEditor to use the {@link MOOSETreeCompositeViewer} instead
	 * of the default TreeCompositeViewer.
	 * 
	 * @return The String ID of the MOOSETreeCompositeViewer
	 */
	@Override
	protected String getTreeCompositeViewerID() {
		return MOOSETreeCompositeView.ID;
	}

	/**
	 * Tells the FormEditor to use the MOOSE input tree instead of the first
	 * available tree added to the Form.
	 */
	@Override
	protected int getTreeCompositeViewerInputID() {
		return MOOSEModel.mooseTreeCompositeId;
	}

}
