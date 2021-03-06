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
package org.eclipse.ice.client.widgets.mesh.properties;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.ice.datastructures.form.mesh.BezierEdge;
import org.eclipse.ice.datastructures.form.mesh.Edge;
import org.eclipse.ice.datastructures.form.mesh.Hex;
import org.eclipse.ice.datastructures.form.mesh.IMeshPart;
import org.eclipse.ice.datastructures.form.mesh.IMeshPartVisitor;
import org.eclipse.ice.datastructures.form.mesh.MeshComponent;
import org.eclipse.ice.datastructures.form.mesh.Polygon;
import org.eclipse.ice.datastructures.form.mesh.PolynomialEdge;
import org.eclipse.ice.datastructures.form.mesh.Quad;
import org.eclipse.ice.datastructures.form.mesh.Vertex;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.properties.tabbed.AbstractPropertySection;
import org.eclipse.ui.views.properties.tabbed.ISection;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

/**
 * This class provides an {@link ISection} for displaying the location of a
 * {@link Vertex} in a modifiable manner.<br>
 * <br>
 * Eventually, this may include the ability to modify the locations of multiple
 * vertices in a single selection.
 * 
 * @author Jordan H. Deyton
 * 
 */
public class VertexSection extends AbstractPropertySection {

	/**
	 * The Vertex whose location is being displayed and/or modified.
	 */
	private Vertex vertex;

	// ---- Section configuration ---- //
	/**
	 * The index of the vertex in its edge's array of vertex IDs or in its
	 * polygon's list of vertices.
	 */
	private final int index;
	// ------------------------------- //

	// ---- X and Y coordinate ---- //
	/**
	 * A Text field for the vertex's x coordinate.
	 */
	private Text xText;
	/**
	 * This listener updates the vertex's x coordinate when {@link #xText} has
	 * changed.
	 */
	private final ModifyListener xListener;
	/**
	 * A Text field for the vertex's y coordinate.
	 */
	private Text yText;
	/**
	 * This listener updates the vertex's x coordinate when {@link #yText} has
	 * changed.
	 */
	private final ModifyListener yListener;
	// ---------------------------- //

	/**
	 * A List of disposable widgets used in the Section.
	 */
	private final List<Control> disposableControls = new ArrayList<Control>();

	/**
	 * The default constructor. This should be used when the selection is
	 * expected to be a Vertex.
	 */
	public VertexSection() {
		// Call the other constructor. The vertex's index will just be ignored.
		this(0);
	}

	/**
	 * This constructor should be used when the selection is expected to be an
	 * Edge or a Polygon.
	 * 
	 * @param vertexIndex
	 *            The index of the vertex with respect to the edge or polygon.
	 */
	public VertexSection(int vertexIndex) {
		// Set the index of the vertex.
		index = (vertexIndex >= 0 ? vertexIndex : 0);

		// Create the listener for the x coordinate's Text field.
		xListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				float[] location = vertex.getLocation();
				// Try to parse the number and set it as the vertex's x
				// coordinate.
				try {
					Float x = Float.parseFloat(xText.getText());
					location[0] = x;
					vertex.setLocation(location);
				}
				// If the number is incorrectly formatted, restore the previous
				// x coordinate.
				catch (NumberFormatException exception) {
					xText.setText(Float.toString(location[0]));
					// No change in the vertex's location.
				}
				return;
			}
		};

		// Create the listener for the y coordinate's Text field.
		yListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				float[] location = vertex.getLocation();
				// Try to parse the number and set it as the vertex's x
				// coordinate.
				Float y;
				try {
					y = Float.parseFloat(yText.getText());
					location[1] = y;
					vertex.setLocation(location);
				}
				// If the number is incorrectly formatted, restore the previous
				// x coordinate.
				catch (NumberFormatException exception) {
					yText.setText(Float.toString(location[1]));
					// No change in the vertex's location.
				}
				return;
			}
		};

		return;
	}

	/**
	 * Creates the controls displayed in the section. It needs to at least
	 * initialize both {@link #xText} and {@link #yText}.
	 */
	@Override
	public void createControls(Composite parent,
			TabbedPropertySheetPage aTabbedPropertySheetPage) {
		// Call the super method first.
		super.createControls(parent, aTabbedPropertySheetPage);

		// Get the background color.
		Color bg = parent.getBackground();

		// Create a Composite to contain the location information.
		Composite composite = new Group(parent, SWT.NONE);
		disposableControls.add(composite);
		composite.setLayout(new GridLayout(2, false));
		composite.setBackground(bg);

		// ---- Create the header label. ---- //
		// Create a label that says "Location".
		Label label = new Label(composite, SWT.LEFT);
		disposableControls.add(label);
		label.setText("Location");
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2,
				1));
		label.setBackground(bg);
		// ---------------------------------- //

		// ---- Create the X coordinate section. ---- //
		// Create the x label.
		label = new Label(composite, SWT.RIGHT);
		disposableControls.add(label);
		label.setText("x:");
		label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		label.setBackground(bg);

		// Create the x text field.
		xText = new Text(composite, SWT.SINGLE | SWT.BORDER);
		disposableControls.add(xText);
		xText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		xText.addModifyListener(xListener);
		// ------------------------------------------ //

		// ---- Create the Y coordinate section. ---- //
		// Create the y label.
		label = new Label(composite, SWT.RIGHT);
		disposableControls.add(label);
		label.setText("y:");
		label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		label.setBackground(bg);

		// Create the y text field.
		yText = new Text(composite, SWT.SINGLE | SWT.BORDER);
		disposableControls.add(yText);
		yText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		yText.addModifyListener(yListener);
		// ------------------------------------------ //

		return;
	}

	/**
	 * This should take a Vertex from the current selection and set
	 * {@link #vertex}.
	 */
	@Override
	public void setInput(IWorkbenchPart part, ISelection selection) {
		super.setInput(part, selection);

		// First, make sure the selection is valid. It should be some type of
		// IStructuredSelection.
		Assert.isTrue(selection instanceof IStructuredSelection);
		IStructuredSelection structuredSelection = (IStructuredSelection) selection;

		// TODO Incorporate a selection of multiple vertices.

		// For now, we are only dealing with the first available element in the
		// selection.
		if (!structuredSelection.isEmpty()) {
			Object element = structuredSelection.getFirstElement();
			Assert.isTrue(element instanceof MeshSelection);
			MeshSelection meshSelection = (MeshSelection) element;

			// Get the selected IMeshPart and mesh from the selection.
			IMeshPart meshPart = meshSelection.selectedMeshPart;
			final MeshComponent mesh = meshSelection.mesh;

			// Create a visitor that can determine the appropriate Vertex
			// instance whose properties are being exposed based on the type of
			// IMeshPart passed in through the selection.
			IMeshPartVisitor visitor = new IMeshPartVisitor() {
				@Override
				public void visit(MeshComponent mesh) {
					// Do nothing.
				}

				@Override
				public void visit(Polygon polygon) {
					// Get the vertex from the polygon.
					ArrayList<Vertex> vertices = polygon.getVertices();
					if (index < vertices.size()) {
						VertexSection.this.vertex = vertices.get(index);
					}
				}

				@Override
				public void visit(Quad quad) {
					// Re-direct to the standard polygon operation for now.
					visit((Polygon) quad);
				}

				@Override
				public void visit(Hex hex) {
					// Re-direct to the standard polygon operation for now.
					visit((Polygon) hex);
				}

				@Override
				public void visit(Edge edge) {
					// Get the vertex ID from the edge, then use the ID to get
					// the vertex from the mesh.
					int[] vertices = edge.getVertexIds();
					if (index < vertices.length && mesh != null) {
						VertexSection.this.vertex = mesh
								.getVertex(vertices[index]);
					}
				}

				@Override
				public void visit(BezierEdge edge) {
					// Re-direct to the standard edge operation for now.
					visit((Edge) edge);
				}

				@Override
				public void visit(PolynomialEdge edge) {
					// Re-direct to the standard edge operation for now.
					visit((Edge) edge);
				}

				@Override
				public void visit(Vertex vertex) {
					VertexSection.this.vertex = vertex;
				}

				@Override
				public void visit(Object object) {
					// Do nothing.
				}
			};

			// Reset the vertex and set it based on the visited IMeshPart.
			vertex = null;
			meshPart.acceptMeshVisitor(visitor);
		}

		return;
	}

	/**
	 * This should dispose of any resources used by this section.
	 */
	@Override
	public void dispose() {
		// Reset the vertex.
		vertex = null;

		// Dispose of all disposable controls. If they are in the list, they
		// were created in createControls.
		for (Control control : disposableControls) {
			if (!control.isDisposed()) {
				control.dispose();
			}
		}
		// Clear the list so that they are not disposed again.
		disposableControls.clear();

		return;
	}

	/**
	 * This should update all of the widgets that show data from the associated
	 * vertex.
	 */
	@Override
	public void refresh() {
		if (vertex != null) {
			// Update the Text fields with their appropriate coordinates.
			float[] location = vertex.getLocation();
			xText.setText(Float.toString(location[0]));
			yText.setText(Float.toString(location[1]));

			// Enable the text fields.
			xText.setEnabled(true);
			yText.setEnabled(true);
		} else {
			// Reset the text fields to a "default" value.
			xText.setText(Float.toString(0f));
			yText.setText(Float.toString(0f));

			// Disable the text fields.
			xText.setEnabled(false);
			yText.setEnabled(false);
		}

		return;
	}
}
