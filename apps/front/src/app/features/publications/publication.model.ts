export interface Publication {
  id: number,
  content: string,
  status: "DRAFT" | "VERIFIED",
}

export type CreatePublicationRequest = Omit<Publication, 'id'>

export interface CreatePublicationFormValue {
  content: Publication['content']
  status: boolean
}