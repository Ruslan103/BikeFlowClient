$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$projectDirectory = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $projectDirectory 'src/main/resources/app-icon.png'
$iconPath = Join-Path $projectDirectory 'tools/assets/craft-biker-app-icon.ico'
$sizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)

$source = [Drawing.Image]::FromFile($sourcePath)
$images = [Collections.Generic.List[byte[]]]::new()
try {
    foreach ($size in $sizes) {
        $bitmap = [Drawing.Bitmap]::new($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.DrawImage($source, 0, 0, $size, $size)

                $imageStream = [IO.MemoryStream]::new()
                try {
                    $bitmap.Save($imageStream, [Drawing.Imaging.ImageFormat]::Png)
                    $images.Add($imageStream.ToArray())
                } finally {
                    $imageStream.Dispose()
                }
            } finally {
                $graphics.Dispose()
            }
        } finally {
            $bitmap.Dispose()
        }
    }

    $iconStream = [IO.MemoryStream]::new()
    try {
        $writer = [IO.BinaryWriter]::new($iconStream)
        try {
            $writer.Write([UInt16]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]$sizes.Count)

            $imageOffset = 6 + 16 * $sizes.Count
            for ($index = 0; $index -lt $sizes.Count; $index++) {
                $size = $sizes[$index]
                $imageBytes = $images[$index]
                $dimension = if ($size -eq 256) { [byte]0 } else { [byte]$size }
                $writer.Write($dimension)
                $writer.Write($dimension)
                $writer.Write([byte]0)
                $writer.Write([byte]0)
                $writer.Write([UInt16]1)
                $writer.Write([UInt16]32)
                $writer.Write([UInt32]$imageBytes.Length)
                $writer.Write([UInt32]$imageOffset)
                $imageOffset += $imageBytes.Length
            }

            foreach ($imageBytes in $images) {
                $writer.Write($imageBytes)
            }
        } finally {
            $writer.Dispose()
        }

        [IO.File]::WriteAllBytes($iconPath, $iconStream.ToArray())
    } finally {
        $iconStream.Dispose()
    }
} finally {
    $source.Dispose()
}

Write-Output "Created $iconPath with sizes: $($sizes -join ', ') px"
