// AI-assisted report date guard keeps invalid filter drafts editable on screen.
export function validReportRange(from,to) {
  const valid=value=> /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value+'T00:00:00Z')) && new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;
  return valid(from) && valid(to) && from<=to && Date.parse(to)-Date.parse(from)<=365*86400000;
}
