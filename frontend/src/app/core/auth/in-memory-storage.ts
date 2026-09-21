import { Injectable } from '@angular/core';
import { AbstractSecurityStorage } from 'angular-auth-oidc-client';

/** Anything whose name suggests it carries a token stays in memory. */
const TOKEN_BEARING = /token|authzData|authnResult|userData/i;

/**
 * Keeps tokens in memory while letting the authorization handshake survive the redirect.
 *
 * The library persists one JSON blob per config, mixing the handshake artifacts (state, nonce,
 * code verifier) with the tokens it later obtains. Those cannot share a lifetime: redirecting to
 * the identity provider tears the application down, so anything held only in memory is gone when
 * the callback arrives and the state can never be matched. Tokens, on the other hand, are
 * obtained *after* the redirect and must not be readable by script from browser storage.
 *
 * So the blob is split: the handshake half is written to sessionStorage, the token half is kept
 * in memory, and reads merge the two. A full page reload therefore loses the tokens and costs one
 * silent redirect through the still-valid SSO session, which is the price of keeping them out of
 * storage at all.
 */
@Injectable()
export class InMemorySecurityStorage implements AbstractSecurityStorage {
  private readonly memory = new Map<string, string>();
  private readonly persistedKeys = new Set<string>();

  read(key: string): string | null {
    const persisted = parse(sessionRead(key));
    const inMemory = parse(this.memory.get(key) ?? null);

    if (persisted === null && inMemory === null) {
      return null;
    }
    if (!isRecord(persisted) || !isRecord(inMemory)) {
      return this.memory.get(key) ?? sessionRead(key);
    }
    return JSON.stringify({ ...persisted, ...inMemory });
  }

  write(key: string, value: string): void {
    this.memory.set(key, value);

    const parsed = parse(value);
    if (!isRecord(parsed)) {
      return;
    }
    const withoutTokens = Object.fromEntries(
      Object.entries(parsed).filter(([name]) => !TOKEN_BEARING.test(name)),
    );
    sessionWrite(key, JSON.stringify(withoutTokens));
    this.persistedKeys.add(key);
  }

  remove(key: string): void {
    this.memory.delete(key);
    this.persistedKeys.delete(key);
    sessionRemove(key);
  }

  clear(): void {
    this.memory.clear();
    this.persistedKeys.forEach(sessionRemove);
    this.persistedKeys.clear();
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parse(value: string | null): unknown {
  if (value === null) {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

// Storage throws in a private window or with site data blocked; the app must still load.
function sessionRead(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function sessionWrite(key: string, value: string): void {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    // Handshake state cannot be persisted; login will fail loudly rather than silently.
  }
}

function sessionRemove(key: string): void {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Nothing to clean up.
  }
}
