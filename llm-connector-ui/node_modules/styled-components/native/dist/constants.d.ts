export declare const SC_ATTR: string;
export declare const SC_ATTR_ACTIVE = "active";
export declare const SC_ATTR_VERSION = "data-styled-version";
export declare const SC_VERSION: string;
export declare const SPLITTER = "/*!sc*/\n";
export declare const IS_BROWSER: boolean;
/**
 * True when running in a React Server Component environment (createContext
 * is unavailable). In browser / standalone / native builds the entire
 * expression is replaced with the literal `false` via rollup-plugin-replace
 * with empty delimiters (exact string match), enabling rollup constant
 * inlining and terser dead-code elimination for all RSC branches.
 */
export declare const IS_RSC: boolean;
export declare const DISABLE_SPEEDY: boolean;
export declare const KEYFRAMES_ID_PREFIX = "sc-keyframes-";
/**
 * Warn once a single styled component or global style emits this many inline
 * <style> tags in one server render. Set well above any hand-written page;
 * only a pathological generated list reaches it, which is exactly the case
 * that wants a shared className (styled components) or a single mount point
 * higher in the tree (global styles). Shared by StyledComponent.ts and
 * createGlobalStyle.ts.
 */
export declare const RSC_REDUNDANT_EMIT_WARN_THRESHOLD = 1000;
export declare const STATIC_EXECUTION_CONTEXT: {};
