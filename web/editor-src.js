import { EditorView, basicSetup } from 'codemirror';
import { EditorState, Compartment } from '@codemirror/state';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { html } from '@codemirror/lang-html';
import { json } from '@codemirror/lang-json';
import { oneDark } from '@codemirror/theme-one-dark';

/**
 * Progressive enhancement: the normal textarea remains functional if npm
 * dependencies are missing or CodeMirror fails to initialize on Android.
 */
export function attachEditor({ textarea, host, lineNumbers }) {
  const target = document.createElement('div');
  target.className = 'm5-codemirror-host';
  host.append(target);
  const language = new Compartment();
  const languageFor = filename => {
    if (/\.(js|jsx|ts|tsx|mjs|cjs)$/i.test(filename)) return javascript({ typescript: /\.(ts|tsx)$/i.test(filename), jsx: /\.(jsx|tsx)$/i.test(filename) });
    if (/\.py$/i.test(filename)) return python();
    if (/\.html?$/i.test(filename)) return html();
    if (/\.json$/i.test(filename)) return json();
    return [];
  };
  const view = new EditorView({
    parent: target,
    state: EditorState.create({
      doc: textarea.value,
      extensions: [
        basicSetup,
        oneDark,
        language.of([]),
        EditorView.theme({
          '&': { height: '100%', fontSize: '13px', backgroundColor: '#191919' },
          '.cm-scroller': { overflow: 'auto', fontFamily: 'ui-monospace,monospace' },
          '.cm-content': { minHeight: '100%' },
          '.cm-gutters': { backgroundColor: '#1b1b1b', borderRight: '1px solid #343434' }
        }),
        EditorView.updateListener.of(update => {
          if (!update.docChanged && !update.selectionSet) return;
          if (update.docChanged) {
            textarea.value = update.state.doc.toString();
            textarea.dispatchEvent(new Event('input'));
          }
          const head = update.state.selection.main.head;
          textarea.setSelectionRange(head, head);
          textarea.dispatchEvent(new Event('keyup'));
        })
      ]
    })
  });
  textarea.style.display = 'none';
  if (lineNumbers) lineNumbers.style.display = 'none';
  return {
    setFile(content, filename) {
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: content },
        effects: language.reconfigure(languageFor(filename))
      });
    },
    focus: () => view.focus(),
    destroy: () => {
      view.destroy();
      target.remove();
      textarea.style.display = '';
      if (lineNumbers) lineNumbers.style.display = '';
    }
  };
}
