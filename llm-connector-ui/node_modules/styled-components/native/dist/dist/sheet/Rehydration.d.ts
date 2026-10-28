import { InsertionTarget } from '../types';
import { Sheet } from './types';
/** Test-only: clears the warn-once flag between test cases. */
export declare const resetVersionMismatchWarning: () => void;
/**
 * Extract the container (Document or ShadowRoot) from an InsertionTarget.
 * If the target is a ShadowRoot, return it directly.
 * If the target is an HTMLElement, return its root node if it's a ShadowRoot, otherwise return document.
 */
export declare const getRehydrationContainer: (target?: InsertionTarget | undefined) => Document | ShadowRoot;
export declare const outputSheet: (sheet: Sheet) => string;
export declare const rehydrateSheet: (sheet: Sheet) => void;
