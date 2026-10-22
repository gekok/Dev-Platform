import { useState } from 'react';

export default function ColumnCreateForm({ onCreate, onCancel, isCreating, error }) {
  const [name, setName] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      await onCreate(name.trim());
      setName('');
    } catch {
      // The parent mutation renders the error state beside this form.
    }
  }

  return (
    <form className="column-create-form" id="column-create-form" onSubmit={handleSubmit}>
      <div>
        <label htmlFor="column-name">Column name</label>
        <input
          autoComplete="off"
          id="column-name"
          maxLength={50}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Ready for review"
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
          {isCreating ? 'Adding…' : 'Add Column'}
        </button>
      </div>
    </form>
  );
}
