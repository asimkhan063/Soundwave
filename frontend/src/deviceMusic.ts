import { Capacitor, registerPlugin } from '@capacitor/core'

export type DeviceTrack = {
  id: string
  title: string
  artist: string
  album: string
  durationSeconds: number
  uri: string
  displayName: string
  mimeType: string
}

type DeviceMusicPlugin = {
  listTracks(): Promise<{ tracks: DeviceTrack[] }>
  copyTrack(options: { id: string; displayName: string; mimeType: string }): Promise<{ path: string }>
}

const DeviceMusic = registerPlugin<DeviceMusicPlugin>('DeviceMusic')

export async function getDeviceTracks() {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('Phone ke offline songs dekhne ke liye Android app kholo.')
  }
  return (await DeviceMusic.listTracks()).tracks
}

export async function getDeviceTrackUrl(track: DeviceTrack) {
  const { path } = await DeviceMusic.copyTrack({ id: track.id, displayName: track.displayName, mimeType: track.mimeType })
  return Capacitor.convertFileSrc(path)
}
