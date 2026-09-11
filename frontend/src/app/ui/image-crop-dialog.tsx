"use client";

import { Crop, LoaderCircle, Move, X, ZoomIn } from "lucide-react";
import { useEffect, useRef, useState } from "react";

type ImageCropDialogProps = {
  file: File;
  aspectRatio: number;
  title: string;
  onCancel: () => void;
  onConfirm: (file: File) => Promise<void> | void;
};

export function ImageCropDialog({
  file,
  aspectRatio,
  title,
  onCancel,
  onConfirm,
}: ImageCropDialogProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const [zoom, setZoom] = useState(1);
  const [offsetX, setOffsetX] = useState(0);
  const [offsetY, setOffsetY] = useState(0);
  const [isReady, setIsReady] = useState(false);
  const [isApplying, setIsApplying] = useState(false);

  const outputWidth = aspectRatio >= 1 ? 1200 : Math.round(1200 * aspectRatio);
  const outputHeight = aspectRatio >= 1 ? Math.round(1200 / aspectRatio) : 1200;

  useEffect(() => {
    const url = URL.createObjectURL(file);
    const image = new window.Image();
    image.onload = () => {
      imageRef.current = image;
      setIsReady(true);
    };
    image.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const image = imageRef.current;
    if (!canvas || !image || !isReady) return;
    const context = canvas.getContext("2d");
    if (!context) return;

    const baseScale = Math.max(outputWidth / image.naturalWidth, outputHeight / image.naturalHeight);
    const scale = baseScale * zoom;
    const sourceWidth = Math.min(image.naturalWidth, outputWidth / scale);
    const sourceHeight = Math.min(image.naturalHeight, outputHeight / scale);
    const maxX = Math.max(0, image.naturalWidth - sourceWidth);
    const maxY = Math.max(0, image.naturalHeight - sourceHeight);
    const sourceX = maxX / 2 + (offsetX / 100) * (maxX / 2);
    const sourceY = maxY / 2 + (offsetY / 100) * (maxY / 2);

    context.clearRect(0, 0, outputWidth, outputHeight);
    context.drawImage(
      image,
      Math.max(0, Math.min(maxX, sourceX)),
      Math.max(0, Math.min(maxY, sourceY)),
      sourceWidth,
      sourceHeight,
      0,
      0,
      outputWidth,
      outputHeight,
    );
  }, [isReady, offsetX, offsetY, outputHeight, outputWidth, zoom]);

  async function applyCrop() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    setIsApplying(true);
    try {
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob((result) => result ? resolve(result) : reject(new Error("이미지를 크롭하지 못했습니다.")), "image/jpeg", 0.9);
      });
      const baseName = file.name.replace(/\.[^.]+$/, "") || "photo";
      await onConfirm(new File([blob], `${baseName}-cropped.jpg`, { type: "image/jpeg", lastModified: Date.now() }));
    } finally {
      setIsApplying(false);
    }
  }

  return (
    <div className="modal-backdrop image-crop-backdrop" role="presentation">
      <section aria-labelledby="image-crop-title" aria-modal="true" className="image-crop-dialog" role="dialog">
        <header>
          <div><span className="panel-icon"><Crop size={20} /></span><div><p className="eyebrow">IMAGE CROP</p><h2 id="image-crop-title">{title}</h2></div></div>
          <button aria-label="크롭 취소" disabled={isApplying} onClick={onCancel} type="button"><X size={20} /></button>
        </header>
        <div className="image-crop-preview">
          {!isReady && <LoaderCircle className="spin" size={26} />}
          <canvas aria-label="크롭 미리보기" height={outputHeight} ref={canvasRef} width={outputWidth} />
        </div>
        <div className="image-crop-controls">
          <label><span><ZoomIn size={16} /> 확대</span><input max="3" min="1" onChange={(event) => setZoom(Number(event.target.value))} step="0.05" type="range" value={zoom} /></label>
          <label><span><Move size={16} /> 가로 위치</span><input max="100" min="-100" onChange={(event) => setOffsetX(Number(event.target.value))} step="1" type="range" value={offsetX} /></label>
          <label><span><Move size={16} /> 세로 위치</span><input max="100" min="-100" onChange={(event) => setOffsetY(Number(event.target.value))} step="1" type="range" value={offsetY} /></label>
        </div>
        <footer>
          <button className="secondary-button" disabled={isApplying} onClick={onCancel} type="button">취소</button>
          <button className="dark-button" disabled={!isReady || isApplying} onClick={() => void applyCrop()} type="button">
            {isApplying && <LoaderCircle className="spin" size={16} />}크롭 적용
          </button>
        </footer>
      </section>
    </div>
  );
}
