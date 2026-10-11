import { useMemo, useState } from "react";
import { Icon } from "../components/Icon";
import { personPath } from "../lib/ops";
import { parsePerson, type Person } from "../lib/person";
import { apply, toast, useSync } from "../store";

/** The file itself, for family, notes and anything the form doesn't cover. */
export function EditMarkdown({ person }: { person: Person }) {
  const path = personPath(person.id);
  const { files } = useSync();
  const [text, setText] = useState(files[path] ?? "");
  const issues = useMemo(() => parsePerson(person.id, text).issues, [person.id, text]);
  const errors = issues.filter((i) => i.level === "error");

  const save = async () => {
    await apply({ type: "replace", path, content: text });
    toast("Saved");
    location.hash = `#/people/${person.id}`;
  };

  return (
    <div className="form page-form">
      <a className="back" href={`#/people/${person.id}`}>
        <Icon name="back" size={18} /> {person.name}
      </a>
      <h1>Edit as Markdown</h1>
      <p className="muted small">
        <code>{path}</code> · format guide in the project's <code>docs/data-format.md</code>
      </p>
      <textarea className="code" rows={22} value={text} onChange={(e) => setText(e.target.value)} spellCheck={false} />
      {issues.length > 0 && (
        <ul className="issues">
          {issues.map((i, n) => (
            <li key={n} className={i.level}>
              {i.message}
            </li>
          ))}
        </ul>
      )}
      <button className="button primary" onClick={save} disabled={errors.length > 0 || text === files[path]}>
        Save
      </button>
    </div>
  );
}
