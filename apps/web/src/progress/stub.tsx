/** Placeholders while a Progress card or page is built (OS-1001); none is left when the phase ends. */
export function ProgressCardStub({ title }: { title: string }) {
  return (
    <div className="panel">
      <h2>{title}</h2>
    </div>
  );
}

export function ProgressPageStub({ title }: { title: string }) {
  return (
    <section className="page">
      <div className="titlebar">
        <h1>{title}</h1>
      </div>
    </section>
  );
}
