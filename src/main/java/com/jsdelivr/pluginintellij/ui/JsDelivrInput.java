package com.jsdelivr.pluginintellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

public abstract class JsDelivrInput implements FocusListener {
	public String placeholder;
	public JBTextField inputField;
	public Editor editor;
	public Project project;
	public JsDelivrPopup popup;
	public boolean loading = false;
	public static JsDelivrList list;

	private Font font;

	public JsDelivrInput(Editor editor, Project project, String placeholder) {
		this.editor = editor;
		this.project = project;
		this.placeholder = placeholder;

		inputField = new JBTextField();
		inputField.addMouseListener(new JsDelivrMouseListener());
		inputField.getDocument().addDocumentListener(new PasteListener());

		list = new JsDelivrList(s -> {
			inputComplete(s.toString());
			popup.closePopup();
			return null;
		});

		list.setFont(getFont());
		list.setMaximumSize(JsDelivrPopup.popupDim);
		popup = new JsDelivrPopup(editor, new JsDelivrKeyDispatcher(), inputField, list.getPane());

		inputField.requestFocus();
		inputField.setForeground(JBColor.GRAY);
		inputField.setText(placeholder);
		inputField.setCaretPosition(0);
	}

	/**
	 * Update autocomplete hints in variable list.
	 *
	 * @param text Text in input field
	 */
	protected abstract void updateAutocomplete(String text);

	/**
	 * Continue to next stage of input process.
	 *
	 * @param text Text in input field
	 * @return Completion success
	 */
	protected abstract boolean inputComplete(String text);

	/**
	 * Go to previous stage of input process.
	 */
	protected abstract void previousInput();

	/**
	 * Support custom key event actions.
	 *
	 * @param keyEvent key event
	 */
	protected void onKeyEvent(KeyEvent keyEvent) {

	}

	@Override
	public void focusLost(FocusEvent event) {
		popup.closePopup();
	}

	@Override
	public void focusGained(FocusEvent event) {
		// no op
	}

	public Font getFont() {
		return this.font;
	}

	public void setFont(Font font) {
		list.setFont(font);
		inputField.setFont(font);
		this.font = font;
	}

	class JsDelivrKeyDispatcher implements KeyEventDispatcher {
		boolean shift = false;
		boolean tab = false;
		private String lastUpdate = "";

		private boolean isAllowedWhenInputEmpty(KeyEvent event) {
			return event.getKeyCode() != KeyEvent.VK_DELETE
				&& event.getKeyCode() != KeyEvent.VK_BACK_SPACE
				&& event.getKeyCode() != KeyEvent.VK_LEFT
				&& event.getKeyCode() != KeyEvent.VK_RIGHT;
		}

		private boolean isAllowedWhenListEmpty(KeyEvent event) {
			return event.getKeyCode() != KeyEvent.VK_ENTER
				&& event.getKeyCode() != KeyEvent.VK_UP
				&& event.getKeyCode() != KeyEvent.VK_DOWN;
		}

		private boolean mustResetPlaceholder(KeyEvent event) {
			return event.getKeyCode() == KeyEvent.VK_SHIFT
				|| event.isActionKey()
				|| event.getKeyCode() == KeyEvent.VK_TAB
				|| event.getKeyCode() == KeyEvent.VK_ALT
				|| event.getKeyCode() == KeyEvent.VK_CONTROL
				|| event.getKeyCode() == KeyEvent.VK_CAPS_LOCK
				|| event.getKeyCode() == KeyEvent.VK_NUM_LOCK
				|| event.getKeyCode() == KeyEvent.VK_DELETE
				|| event.getKeyCode() == KeyEvent.VK_BACK_SPACE
				|| (event.getKeyCode() == KeyEvent.VK_DELETE && event.isShiftDown());
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent ke) {
			if ((!isAllowedWhenInputEmpty(ke) && inputField.getText().equals(placeholder))
				|| (!isAllowedWhenListEmpty(ke) && list.getDefaultModel().isEmpty())
			) {
				ke.consume();
				return true;
			}

			if (ke.getID() == KeyEvent.KEY_PRESSED) {
				if (inputField.getText().equals(placeholder)) {
					if (isAllowedWhenInputEmpty(ke) && !mustResetPlaceholder(ke)) {
						inputField.setForeground(editor.getContentComponent().getForeground());
						inputField.setText("");
					} else {
						inputField.setText(placeholder);
						inputField.setCaretPosition(0);
					}
				}

				if (ke.getKeyCode() == KeyEvent.VK_SHIFT) {
					shift = true;
					ke.consume();
				} else if (ke.getKeyCode() == KeyEvent.VK_TAB) {
					tab = true;
					ke.consume();
				}
			}

			if (ke.getID() == KeyEvent.KEY_RELEASED) {
				if ((ke.getKeyCode() == KeyEvent.VK_TAB && (shift || ke.isShiftDown())) || (ke.getKeyCode() == KeyEvent.VK_SHIFT && tab)) {
					shift = ke.getKeyCode() == KeyEvent.VK_TAB;
					tab = ke.getKeyCode() == KeyEvent.VK_SHIFT;

					ke.consume();

					if (!loading) {
						previousInput();
					}
				} else if (ke.getKeyCode() == KeyEvent.VK_ENTER || ke.getKeyCode() == KeyEvent.VK_TAB) {
					tab = tab && ke.getKeyCode() != KeyEvent.VK_TAB;
					ke.consume();

					if (list.getDefaultModel().isEmpty()) {
						inputField.setText(placeholder);
						inputField.setCaretPosition(0);
						return false;
					}

					if (inputComplete(list.getSelectedItem().toString()) && !loading) {
						popup.closePopup();
					}
				} else if (ke.getKeyCode() == KeyEvent.VK_SHIFT) {
					shift = false;
					ke.consume();
				} else if (ke.getKeyCode() != KeyEvent.VK_UP && ke.getKeyCode() != KeyEvent.VK_DOWN) {
					if (inputField.getText().equals("")) {
						inputField.setForeground(JBColor.GRAY);
						inputField.setText(placeholder);
						inputField.setCaretPosition(0);
						updateAutocomplete("");
						list.resetSelection();
						lastUpdate = "";
					} else if ((!mustResetPlaceholder(ke) || ke.getKeyCode() == KeyEvent.VK_BACK_SPACE || ke.getKeyCode() == KeyEvent.VK_DELETE) && !inputField.getText().equals(placeholder)) {
						if (!inputField.getText().equals(lastUpdate)) {
							lastUpdate = inputField.getText();
							updateAutocomplete(inputField.getText());
							list.resetSelection();
						}
					}
				}
			}

			onKeyEvent(ke);
			list.keyEvent(ke);
			return false;
		}
	}

	class PasteListener implements DocumentListener {
		@Override
		public void insertUpdate(DocumentEvent event) {
			ApplicationManager.getApplication().invokeLater(() -> {
				ApplicationManager.getApplication().runWriteAction(() -> {
					if (inputField.getText().contains(placeholder) && inputField.getText().length() > placeholder.length()) {
						inputField.setForeground(editor.getContentComponent().getForeground());
						inputField.setText(inputField.getText().substring(0, event.getLength()));
					}
				});
			});
		}

		@Override
		public void removeUpdate(DocumentEvent event) {
			// no op
		}

		@Override
		public void changedUpdate(DocumentEvent event) {
			// no op
		}
	}

	class JsDelivrMouseListener implements MouseListener {
		@Override
		public void mouseClicked(MouseEvent event) {
			if (inputField.getText().equals(placeholder)) {
				event.consume();
				inputField.select(0, 0);
			}
		}

		@Override
		public void mousePressed(MouseEvent event) {
			if (inputField.getText().equals(placeholder)) {
				event.consume();
				inputField.select(0, 0);
			}
		}

		@Override
		public void mouseReleased(MouseEvent event) {
			if (inputField.getText().equals(placeholder)) {
				event.consume();
				inputField.select(0, 0);
			}
		}

		@Override
		public void mouseEntered(MouseEvent event) {
			// no op
		}

		@Override
		public void mouseExited(MouseEvent event) {
			// no op
		}
	}
}
