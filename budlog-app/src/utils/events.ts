export function switchValue(event: Event): boolean {
  const switchEvent = event as Event & { detail?: { value?: boolean } };
  return Boolean(switchEvent.detail?.value);
}
