import React from 'react';
import StyleSheet from '../sheet';
/**
 * The one member of React 18+'s `PipeableStream` (what `renderToPipeableStream`
 * returns) read here, declared locally because `react-dom/server` types before 18
 * do not export it. See docs/build-architecture.md, "Type package dependencies".
 */
interface PipeableStream {
    pipe: <Writable extends NodeJS.WritableStream>(destination: Writable) => Writable;
}
export default class ServerStyleSheet {
    instance: StyleSheet;
    sealed: boolean;
    constructor({ nonce }?: {
        nonce?: string;
    });
    _emitSheetCSS: () => string;
    collectStyles(children: any): React.JSX.Element;
    getStyleTags: () => string;
    getStyleElement: () => React.JSX.Element[];
    interleaveWithNodeStream(input: NodeJS.ReadableStream | PipeableStream): NodeJS.ReadWriteStream;
    seal: () => void;
}
export {};
