export const downsampleFloat32 = (input, sourceRate, targetRate = 16000) => {
  if (targetRate === sourceRate) return input
  if (targetRate > sourceRate) {
    throw new Error('Target sample rate must be lower than source sample rate')
  }
  const ratio = sourceRate / targetRate
  const outputLength = Math.floor(input.length / ratio)
  const output = new Float32Array(outputLength)

  for (let i = 0; i < outputLength; i += 1) {
    const start = Math.floor(i * ratio)
    const end = Math.min(Math.floor((i + 1) * ratio), input.length)
    let sum = 0
    let count = 0
    for (let j = start; j < end; j += 1) {
      sum += input[j]
      count += 1
    }
    output[i] = count ? sum / count : 0
  }

  return output
}

export const float32ToPcm16Buffer = (input) => {
  const buffer = new ArrayBuffer(input.length * 2)
  const view = new DataView(buffer)

  for (let i = 0; i < input.length; i += 1) {
    const sample = Math.max(-1, Math.min(1, input[i]))
    view.setInt16(i * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }

  return buffer
}
