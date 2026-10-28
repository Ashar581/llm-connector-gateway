import StyleSheet from '../sheet';
import { ExecutionContext, RuleSet, Stringifier } from '../types';
/**
 * Get compiled CSS for a single class name (RSC only), or null when the name
 * was never compiled. Every name reaching RSC emission was compiled by
 * generateAndInjectStyles before it registered its class, so that path's lookup
 * always hits; the null case covers only names that never went through
 * compilation. Lets the RSC emission path pull exactly the current render's
 * class without concatenating (and then filtering) every dynamic variant a
 * component has produced.
 */
export declare function getCompiledCSSForName(cs: ComponentStyle, name: string): string | null;
/**
 * ComponentStyle is all the CSS-specific stuff, not the React-specific stuff.
 */
export default class ComponentStyle {
    baseHash: number;
    baseStyle: ComponentStyle | null | undefined;
    componentId: string;
    rules: RuleSet<any>;
    dynamicNameCache: Map<string, string> | undefined;
    constructor(rules: RuleSet<any>, componentId: string, baseStyle?: ComponentStyle | undefined);
    generateAndInjectStyles(executionContext: ExecutionContext, styleSheet: StyleSheet, stylis: Stringifier): string;
}
