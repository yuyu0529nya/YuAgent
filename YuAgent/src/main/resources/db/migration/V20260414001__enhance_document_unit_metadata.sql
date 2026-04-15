ALTER TABLE public.document_unit
    ADD COLUMN IF NOT EXISTS title_path VARCHAR(1000);

ALTER TABLE public.document_unit
    ADD COLUMN IF NOT EXISTS segment_type VARCHAR(32);

ALTER TABLE public.document_unit
    ADD COLUMN IF NOT EXISTS segment_order INTEGER;

ALTER TABLE public.document_unit
    ADD COLUMN IF NOT EXISTS source_page INTEGER;

ALTER TABLE public.document_unit
    ADD COLUMN IF NOT EXISTS metadata_json TEXT;

CREATE INDEX IF NOT EXISTS idx_document_unit_file_source_page
    ON public.document_unit (file_id, source_page);

CREATE INDEX IF NOT EXISTS idx_document_unit_file_segment_order
    ON public.document_unit (file_id, segment_order);

COMMENT ON COLUMN public.document_unit.title_path IS 'Structured heading path for the chunk';
COMMENT ON COLUMN public.document_unit.segment_type IS 'Segment type such as section, text, table';
COMMENT ON COLUMN public.document_unit.segment_order IS 'Stable ordering of the chunk inside the file';
COMMENT ON COLUMN public.document_unit.source_page IS 'Original source page before vector segment expansion';
COMMENT ON COLUMN public.document_unit.metadata_json IS 'Serialized metadata captured during document parsing';
