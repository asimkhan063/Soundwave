import { registerPlugin, type PluginListenerHandle } from '@capacitor/core'

export type NativePlaybackState = {
  isPlaying: boolean
  positionSeconds: number
  durationSeconds: number
  ended: boolean
}

type BackgroundPlayerPlugin = {
  playTrack(options: { uri: string; title: string; artist: string; artworkUrl: string }): Promise<void>
  pause(): Promise<void>
  resume(): Promise<void>
  seekTo(options: { positionSeconds: number }): Promise<void>
  addListener(eventName: 'playbackState', listener: (state: NativePlaybackState) => void): Promise<PluginListenerHandle>
  addListener(eventName: 'trackEnded', listener: () => void): Promise<PluginListenerHandle>
}

export const backgroundPlayer = registerPlugin<BackgroundPlayerPlugin>('BackgroundPlayer')
