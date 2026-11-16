import ImageIO
import Metal
import UIKit

/// The Earth textures (`shared/data/textures/`, one per `GlobeStyle`) as the
/// globe draws them: at their full size, but ASTC-compressed, so the GPU holds
/// 8 bits per pixel rather than 32.
///
/// An 8192×4096 texture is 134 MB as RGBA, 179 MB with its mipmaps; as ASTC 4×4
/// it is 45 MB with them, at a quality that can't be told from the JPEG it came
/// from (about 44 dB). Every device that runs the app samples ASTC natively.
///
/// The bundle carries only the JPEGs. The first time a style is shown it is
/// compressed on the device — ImageIO's encoder takes a fraction of a second —
/// and written to Caches; after that showing it is a file read, with no JPEG
/// decode at all. Only the style in use is kept there. Until the first
/// compression is done the globe shows `preview`, and where it can't be done
/// (a GPU without ASTC, a failed encode) it gets `fallbackImage`.
///
/// The map draws with Core Graphics, which can't read ASTC: it decodes the
/// JPEG itself (`MapView.MapImage`), from the same `url`.
enum EarthTexture {
    /// Where a texture is in the bundle. Never `UIImage(named:)`: its system
    /// cache keeps a second decoded copy alive.
    static func url(named name: String) -> URL? {
        Bundle.main.url(forResource: name, withExtension: nil)
    }

    // MARK: - Layout

    /// ASTC 4×4: every 4×4 block of pixels is 16 bytes.
    static let blockSize = 4
    static let bytesPerBlock = 16

    /// One mipmap level of a compressed texture.
    struct Level: Equatable {
        let width: Int
        let height: Int

        var blocksWide: Int { (width + EarthTexture.blockSize - 1) / EarthTexture.blockSize }
        var blocksHigh: Int { (height + EarthTexture.blockSize - 1) / EarthTexture.blockSize }
        var bytesPerRow: Int { blocksWide * EarthTexture.bytesPerBlock }
        var byteCount: Int { bytesPerRow * blocksHigh }
    }

    /// The whole mipmap chain of a `width` × `height` texture, down to 1×1.
    static func levels(width: Int, height: Int) -> [Level] {
        var levels = [Level(width: width, height: height)]
        while let last = levels.last, last.width > 1 || last.height > 1 {
            levels.append(Level(width: max(1, last.width / 2), height: max(1, last.height / 2)))
        }
        return levels
    }

    /// A compressed texture as it is cached: a 16-byte header — `magic`, the
    /// width and the height, then a reserved word — followed by every level's
    /// blocks, largest first.
    static let magic: UInt32 = 0x3158_5445 // "ETX1"
    static let headerSize = 16

    /// The size of the texture in `file`, if `file` is a whole compressed texture.
    static func size(ofCompressed file: Data) -> (width: Int, height: Int)? {
        guard file.count > headerSize else { return nil }
        let words = (0..<3).map { file.uint32(at: $0 * 4) }
        guard words[0] == magic, words[1] > 0, words[2] > 0 else { return nil }
        let (width, height) = (Int(words[1]), Int(words[2]))
        let expected = levels(width: width, height: height).reduce(headerSize) { $0 + $1.byteCount }
        return file.count == expected ? (width, height) : nil
    }

    // MARK: - Compressing

    /// The texture at `url`, compressed with all its mipmaps (see `magic` for
    /// the layout), or nil if any level fails to encode.
    static func compressed(contentsOf url: URL) -> Data? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int else { return nil }

        let levels = levels(width: width, height: height)
        var file = Data(capacity: levels.reduce(headerSize) { $0 + $1.byteCount })
        for word in [magic, UInt32(width), UInt32(height), 0] {
            withUnsafeBytes(of: word.littleEndian) { file.append(contentsOf: $0) }
        }

        // The largest level is the image itself. The next is decoded from the
        // file at half size, which JPEG does cheaply and without the full
        // bitmap; the rest are each the level above, halved.
        var previous: CGImage?
        for (index, level) in levels.enumerated() {
            let appended: Bool = autoreleasepool {
                let image: CGImage?
                switch index {
                case 0: image = CGImageSourceCreateImageAtIndex(source, 0, nil)
                case 1: image = reduced(source, maxPixelSize: max(level.width, level.height))
                default: image = previous.flatMap { resized($0, to: level) }
                }
                guard let image, image.width == level.width, image.height == level.height,
                      let blocks = astcBlocks(of: image, level: level) else { return false }
                file.append(blocks)
                previous = index == 0 ? nil : image
                return true
            }
            guard appended else { return nil }
        }
        return file
    }

    /// `image` as ASTC 4×4 blocks, rows top-down, or nil if it can't be encoded.
    ///
    /// ImageIO writes ASTC only inside a KTX file, so the blocks are cut out of
    /// one: a 64-byte header, its key/value data, then the level's byte count
    /// and the blocks. The block size is left to ImageIO, whose default is 4×4
    /// (the key to ask for it by name needs iOS 26); the byte count tells
    /// whether that held.
    static func astcBlocks(of image: CGImage, level: Level) -> Data? {
        let ktx = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(ktx, "org.khronos.ktx" as CFString, 1, nil) else {
            return nil
        }
        CGImageDestinationAddImage(destination, image,
                                   [kCGImagePropertyEncoder: kCGImagePropertyASTCEncoder] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }

        let file = ktx as Data
        let ktxHeaderSize = 64
        guard file.count > ktxHeaderSize + 4 else { return nil }
        let blocksStart = ktxHeaderSize + Int(file.uint32(at: 60)) + 4
        guard blocksStart <= file.count, file.count - blocksStart == level.byteCount,
              Int(file.uint32(at: blocksStart - 4)) == level.byteCount else { return nil }
        return file.subdata(in: blocksStart..<file.count)
    }

    /// The image in `source`, decoded no larger than `maxPixelSize` on its
    /// longer side — without ever holding it at full size.
    private static func reduced(_ source: CGImageSource, maxPixelSize: Int) -> CGImage? {
        CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
            kCGImageSourceShouldCacheImmediately: true,
        ] as CFDictionary)
    }

    private static func resized(_ image: CGImage, to level: Level) -> CGImage? {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: level.width, height: level.height,
                                      bitsPerComponent: 8, bytesPerRow: 0, space: space,
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: level.width, height: level.height))
        return context.makeImage()
    }

    // MARK: - Uncompressed stand-ins

    /// A small copy of the texture, quick enough to decode on the main thread:
    /// what the globe shows while a style is compressed for the first time.
    static func preview(named name: String) -> CGImage? {
        uncompressed(named: name, maxPixelSize: 1024)
    }

    /// The texture where it can't be compressed: uncompressed, so held to
    /// 4096 px — 32 MB rather than 134 MB — as Android's `EarthTextureCache` does.
    static func fallbackImage(named name: String) -> CGImage? {
        uncompressed(named: name, maxPixelSize: 4096)
    }

    private static func uncompressed(named name: String, maxPixelSize: Int) -> CGImage? {
        guard let url = url(named: name),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        return reduced(source, maxPixelSize: maxPixelSize)
    }

    // MARK: - Texture

    /// Whether `device` samples ASTC, which every Apple GPU since the A8 does.
    /// A simulator on an Intel Mac does not.
    static func supportsASTC(_ device: MTLDevice) -> Bool {
        device.supportsFamily(.apple2)
    }

    /// The compressed texture in `file`, uploaded with its mipmaps.
    static func makeTexture(fromCompressed file: Data, device: MTLDevice) -> MTLTexture? {
        guard supportsASTC(device), let size = size(ofCompressed: file) else { return nil }
        let levels = levels(width: size.width, height: size.height)

        let descriptor = MTLTextureDescriptor()
        descriptor.pixelFormat = .astc_4x4_srgb
        descriptor.width = size.width
        descriptor.height = size.height
        descriptor.mipmapLevelCount = levels.count
        descriptor.usage = .shaderRead
        guard let texture = device.makeTexture(descriptor: descriptor) else { return nil }

        file.withUnsafeBytes { bytes in
            guard let base = bytes.baseAddress else { return }
            var offset = headerSize
            for (index, level) in levels.enumerated() {
                texture.replace(region: MTLRegionMake2D(0, 0, level.width, level.height),
                                mipmapLevel: index, slice: 0,
                                withBytes: base + offset,
                                bytesPerRow: level.bytesPerRow, bytesPerImage: level.byteCount)
                offset += level.byteCount
            }
        }
        return texture
    }

    // MARK: - Cache

    /// The texture last made, so a globe view made again shares it rather than
    /// uploading a second copy. Main thread only.
    private static var current: (name: String, texture: MTLTexture)?

    private static let queue = DispatchQueue(label: "com.anmol.voyage.earth-texture", qos: .userInitiated)

    private static var cacheDirectory: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("EarthTexture", isDirectory: true)
    }

    /// Where `name`'s compressed texture is cached. The source's byte count is
    /// in the file name, so a texture that changes in an update is compressed
    /// again rather than read stale.
    private static func cacheURL(named name: String) -> URL? {
        guard let source = url(named: name),
              let byteCount = try? source.resourceValues(forKeys: [.fileSizeKey]).fileSize else { return nil }
        return cacheDirectory?.appendingPathComponent("\(name).\(byteCount).astc")
    }

    private static func cachedFile(named name: String) -> Data? {
        guard let url = cacheURL(named: name),
              let file = try? Data(contentsOf: url, options: .alwaysMapped),
              size(ofCompressed: file) != nil else { return nil }
        return file
    }

    /// Caches `file` as `name`'s texture and drops every other style's, so the
    /// cache never holds more than the style in use.
    private static func store(_ file: Data, named name: String) {
        guard let directory = cacheDirectory, let url = cacheURL(named: name) else { return }
        let files = FileManager.default
        try? files.createDirectory(at: directory, withIntermediateDirectories: true)
        guard (try? file.write(to: url, options: .atomic)) != nil else { return }
        for other in (try? files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
        where other.lastPathComponent != url.lastPathComponent {
            try? files.removeItem(at: other)
        }
    }

    /// `name`'s texture if it takes no compressing — it is in memory or in the
    /// cache — which is quick enough for the main thread. Nil sends the caller
    /// to `load`.
    static func cached(named name: String, device: MTLDevice) -> MTLTexture? {
        if let current, current.name == name { return current.texture }
        guard supportsASTC(device), let file = cachedFile(named: name),
              let texture = makeTexture(fromCompressed: file, device: device) else { return nil }
        current = (name, texture)
        return texture
    }

    /// Compresses `name`'s texture off the main thread, caches it, and hands
    /// `completion` — on the main thread — what the globe's material should
    /// show: the `MTLTexture`, or `fallbackImage` where that can't be made.
    static func load(named name: String, device: MTLDevice, completion: @escaping (Any?) -> Void) {
        queue.async {
            var texture: MTLTexture?
            if supportsASTC(device) {
                var file = cachedFile(named: name)
                if file == nil, let url = url(named: name), let compressed = compressed(contentsOf: url) {
                    store(compressed, named: name)
                    file = compressed
                }
                texture = file.flatMap { makeTexture(fromCompressed: $0, device: device) }
            }
            let fallback = texture == nil ? fallbackImage(named: name) : nil
            DispatchQueue.main.async {
                if let texture { current = (name, texture) }
                completion(texture ?? fallback)
            }
        }
    }
}

private extension Data {
    /// The little-endian 32-bit word `offset` bytes in.
    func uint32(at offset: Int) -> UInt32 {
        withUnsafeBytes { UInt32(littleEndian: $0.loadUnaligned(fromByteOffset: offset, as: UInt32.self)) }
    }
}
