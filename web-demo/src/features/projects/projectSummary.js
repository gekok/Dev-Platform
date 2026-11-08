export function getProjectCardMetadata(project) {
  const metadata = [];

  if (Number.isSafeInteger(project?.boardCount) && project.boardCount >= 0) {
    metadata.push({ key: 'boardCount', label: `${project.boardCount} boards` });
  }

  if (typeof project?.createdAt === 'string') {
    const createdAt = new Date(project.createdAt);
    if (Number.isFinite(createdAt.getTime())) {
      metadata.push({
        key: 'createdAt',
        label: `Created ${createdAt.toLocaleDateString('en-US', { dateStyle: 'medium' })}`,
      });
    }
  }

  return metadata;
}
