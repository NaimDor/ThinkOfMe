import { randomInt } from "node:crypto";

// No 0/O, 1/I/L: codes are sometimes read aloud or typed by hand.
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
export const INVITE_CODE_LENGTH = 8;
export const INVITE_TTL_DAYS = 7;

export function generateInviteCode(length = INVITE_CODE_LENGTH): string {
  let code = "";
  for (let i = 0; i < length; i++) code += ALPHABET[randomInt(ALPHABET.length)];
  return code;
}

/** Normalize user input ("abcd-2345", " ABCD2345 ") into a stored code. */
export function normalizeInviteCode(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const code = input.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (code.length !== INVITE_CODE_LENGTH) return null;
  return [...code].every((ch) => ALPHABET.includes(ch)) ? code : null;
}

export type CircleKind = "pair" | "group";

export function maxMembers(kind: CircleKind): number {
  return kind === "pair" ? 2 : 50;
}

/** Host of the Hosting site that serves invite pages and app-link files. */
export const LINK_HOST = "thinkofme.web.app";

export function inviteLink(code: string): string {
  return `https://${LINK_HOST}/i/${code}`;
}
