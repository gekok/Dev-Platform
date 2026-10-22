import { useState } from 'react';

export default function CardCreateForm({
  columnId,
  members,
  isCreating,
  error,
  onCancel,
  onCreate,
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [validationError, setValidationError] = useState(null);
  const fieldPrefix = `card-create-${columnId}`;

  async function handleSubmit(event) {
    event.preventDefault();
    const cleanTitle = title.trim();
    if (!cleanTitle) {
      setValidationError('Card title is required.');
      return;
    }

    setValidationError(null);
    try {
      await onCreate({
        columnId,
        title: cleanTitle,
        description: description.trim() || null,
        dueDate: dueDate ? `${dueDate}T00:00:00.000Z` : null,
        assigneeId: assigneeId || null,
      });
    } catch {
      // The parent mutation renders its API error beside this form.
    }
  }

  return (
    <form
      className="card-create-form"
      id={`card-create-form-${columnId}`}
      onSubmit={handleSubmit}
    >
      <div>
        <label htmlFor={`${fieldPrefix}-title`}>Card title</label>
        <input
          autoComplete="off"
          id={`${fieldPrefix}-title`}
          maxLength={500}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="What needs to be done?"
          required
          value={title}
        />
      </div>
      <div>
        <label htmlFor={`${fieldPrefix}-description`}>Description</label>
        <textarea
          id={`${fieldPrefix}-description`}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Add a little context"
          rows={3}
          value={description}
        />
      </div>
      <div className="card-create-fields">
        <div>
          <label htmlFor={`${fieldPrefix}-due-date`}>Due date</label>
          <input
            id={`${fieldPrefix}-due-date`}
            onChange={(event) => setDueDate(event.target.value)}
            type="date"
            value={dueDate}
          />
        </div>
        <div>
          <label htmlFor={`${fieldPrefix}-assignee`}>Assignee</label>
          <select
            id={`${fieldPrefix}-assignee`}
            onChange={(event) => setAssigneeId(event.target.value)}
            value={assigneeId}
          >
            <option value="">Unassigned</option>
            {members.map((member) => (
              <option key={member.id} value={member.id}>{member.fullName}</option>
            ))}
          </select>
        </div>
      </div>
      {(validationError || error) && (
        <p className="form-inline-error" role="alert">{validationError ?? error}</p>
      )}
      <div className="form-actions">
        <button className="secondary-button" disabled={isCreating} onClick={onCancel} type="button">
          Cancel
        </button>
        <button className="primary-button" disabled={isCreating} type="submit">
          {isCreating ? 'Creating…' : 'Create Card'}
        </button>
      </div>
    </form>
  );
}
