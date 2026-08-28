export function getSchemaPropertyDescription(value: unknown): string | null {
  if (typeof value !== "object" || value === null || !("description" in value)) {
    return null
  }

  const description = value.description
  return typeof description === "string" ? description : null
}
