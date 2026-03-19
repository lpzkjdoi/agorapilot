export type Publication = {
  id: number,
  content: string,
  status: "DRAFT" | "VERIFIED",
}

export type CreatePublicationRequest = Omit<Publication, 'id'>

export type CreatePublicationFormValue = {
  content: Publication['content']
  status: boolean
}