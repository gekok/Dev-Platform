import { useState } from 'react';

export default function ProjectCreateForm({ onCreate, onCancel, isCreating, error }) {
  const [name, setName] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      await onCreate(name.trim());
      setName('');
    } catch {
      // The parent mutation renders its error state beside this form.
    }
  }

  return (
    <form id="project-create-form" className="project-create-form" onSubmit={handleSubmit}>
      <div>
        <label htmlFor="project-name">Project name</label>
        <input
          id="project-name"
          maxLength={100}
          minLength={1}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Product roadmap"
          required
          value={name}
        />
      </div>
      {error && <p className="form-inline-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button className="secondary-button" disabled={isCreating} onClick={onCancel} type="button">
          Cancel
        </button>
        <button className="primary-button" disabled={isCreating} type="submit">
          {isCreating ? 'Creating…' : 'Create project'}
        </button>
      </div>
    </form>
  );
}
