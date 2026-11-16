import Metal
import XCTest
@testable import voyage

/// The globe's compressed Earth texture: its layout, and that the bundled
/// textures compress into it at their full size.
final class EarthTextureTests: XCTestCase {
    func testMipmapChainRunsDownToOnePixel() {
        let levels = EarthTexture.levels(width: 8192, height: 4096)
        XCTAssertEqual(levels.count, 14)
        XCTAssertEqual(levels.first, EarthTexture.Level(width: 8192, height: 4096))
        XCTAssertEqual(levels[12], EarthTexture.Level(width: 2, height: 1))
        XCTAssertEqual(levels.last, EarthTexture.Level(width: 1, height: 1))
    }

    func testALevelIsOneByteAPixel() {
        // 16 bytes for every 4×4 block, and a whole block for a part of one
        XCTAssertEqual(EarthTexture.Level(width: 8192, height: 4096).byteCount, 8192 * 4096)
        XCTAssertEqual(EarthTexture.Level(width: 2, height: 1).byteCount, 16)
        XCTAssertEqual(EarthTexture.Level(width: 6, height: 4).bytesPerRow, 32)
    }

    /// The whole 8192 px texture with its mipmaps is under 45 MB, where RGBA
    /// would be 179 MB.
    func testAnEightThousandPixelTextureIsUnderFortyFiveMegabytes() {
        let bytes = EarthTexture.levels(width: 8192, height: 4096).reduce(0) { $0 + $1.byteCount }
        XCTAssertLessThan(bytes, 45 * 1024 * 1024)
    }

    func testEveryStyleCompressesAtItsFullSize() throws {
        let sizes: [GlobeStyle: Int] = [.realistic: 8192, .natural: 8192, .stylized: 4096]
        for style in GlobeStyle.allCases {
            let url = try XCTUnwrap(EarthTexture.url(named: style.textureName))
            let file = try XCTUnwrap(EarthTexture.compressed(contentsOf: url), "\(style)")
            let size = try XCTUnwrap(EarthTexture.size(ofCompressed: file), "\(style)")
            XCTAssertEqual(size.width, sizes[style], "\(style)")
            XCTAssertEqual(size.height, size.width / 2, "\(style)")
        }
    }

    func testATruncatedOrForeignFileIsNotATexture() throws {
        let url = try XCTUnwrap(EarthTexture.url(named: GlobeStyle.stylized.textureName))
        let file = try XCTUnwrap(EarthTexture.compressed(contentsOf: url))
        XCTAssertNil(EarthTexture.size(ofCompressed: file.dropLast(1)))
        XCTAssertNil(EarthTexture.size(ofCompressed: Data(count: file.count)))
        XCTAssertNil(EarthTexture.size(ofCompressed: Data()))
    }

    func testACompressedTextureUploadsWithItsMipmaps() throws {
        let device = try XCTUnwrap(MTLCreateSystemDefaultDevice())
        try XCTSkipUnless(EarthTexture.supportsASTC(device), "This GPU does not sample ASTC")
        let url = try XCTUnwrap(EarthTexture.url(named: GlobeStyle.realistic.textureName))
        let file = try XCTUnwrap(EarthTexture.compressed(contentsOf: url))
        let texture = try XCTUnwrap(EarthTexture.makeTexture(fromCompressed: file, device: device))
        XCTAssertEqual(texture.width, 8192)
        XCTAssertEqual(texture.height, 4096)
        XCTAssertEqual(texture.mipmapLevelCount, 14)
        XCTAssertEqual(texture.pixelFormat, .astc_4x4_srgb)
    }

    func testStandInsAreHeldToTheirSizes() throws {
        let name = GlobeStyle.realistic.textureName
        XCTAssertEqual(try XCTUnwrap(EarthTexture.preview(named: name)).width, 1024)
        XCTAssertEqual(try XCTUnwrap(EarthTexture.fallbackImage(named: name)).width, 4096)
    }
}
