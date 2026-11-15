import i18n from '../i18n/i18n';

/** Localize legacy display literals while keeping English as their readable fallback. */
export function translateUiText(text: string): string {
  return text ? i18n.t(text, { defaultValue: text }) as string : text;
}
