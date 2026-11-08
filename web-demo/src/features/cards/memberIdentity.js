export function getMemberUserId(member) {
  return member?.userId ?? member?.id ?? null;
}

export function getMemberDisplayName(member) {
  return typeof member?.fullName === 'string' && member.fullName.trim().length > 0
    ? member.fullName
    : 'Project member';
}
